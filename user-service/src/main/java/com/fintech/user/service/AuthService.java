package com.fintech.user.service;

import com.fintech.common.exception.BusinessException;
import com.fintech.user.dto.AuthDto;
import com.fintech.user.entity.RefreshToken;
import com.fintech.user.entity.User;
import com.fintech.user.repository.RefreshTokenRepository;
import com.fintech.user.repository.UserRepository;
import com.fintech.user.security.JwtService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private static final String INVALID_REFRESH_MESSAGE = "Geçersiz veya süresi dolmuş refresh token";

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    @Transactional
    public AuthenticationResult register(AuthDto.RegisterRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new BusinessException("Bu kullanıcı adı zaten kullanılıyor", "USERNAME_EXISTS", HttpStatus.CONFLICT);
        }
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new BusinessException("Bu email adresi zaten kullanılıyor", "EMAIL_EXISTS", HttpStatus.CONFLICT);
        }

        User user = User.builder()
                .username(request.getUsername())
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .phoneNumber(request.getPhoneNumber())
                .build();

        user = userRepository.save(user);
        log.info("Yeni kullanıcı kaydedildi: {}", user.getUsername());
        return issueTokenFamily(user);
    }

    @Transactional
    public AuthenticationResult login(AuthDto.LoginRequest request) {
        User user = userRepository.findByUsername(request.getUsernameOrEmail())
                .or(() -> userRepository.findByEmail(request.getUsernameOrEmail()))
                .orElseThrow(() -> invalidCredentials());

        validateActiveUser(user);

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw invalidCredentials();
        }

        user.setLastLoginAt(Instant.now());
        userRepository.save(user);

        log.info("Kullanıcı giriş yaptı: {}", user.getUsername());
        return issueTokenFamily(user);
    }

    /**
     * Pessimistic lock aynı refresh token'ın iki eşzamanlı istekte başarıyla
     * kullanılmasını engeller. Reuse tespitindeki aile iptali BusinessException
     * fırlatılsa da kalıcı olmalıdır.
     */
    @Transactional(noRollbackFor = BusinessException.class)
    public AuthenticationResult refreshToken(String rawRefreshToken) {
        Claims claims = parseRefreshClaims(rawRefreshToken);
        String tokenHash = hashToken(rawRefreshToken);
        RefreshToken storedToken = refreshTokenRepository.findByTokenHashForUpdate(tokenHash)
                .orElseThrow(this::invalidRefreshToken);

        Instant now = Instant.now();
        UUID claimedTokenId = parseUuid(claims.getId());
        UUID claimedFamilyId = parseUuid(claims.get(JwtService.FAMILY_ID_CLAIM, String.class));
        Long claimedUserId = extractUserId(claims);

        if (!storedToken.getId().equals(claimedTokenId)
                || !storedToken.getFamilyId().equals(claimedFamilyId)
                || !Objects.equals(storedToken.getUser().getId(), claimedUserId)) {
            revokeFamily(storedToken.getFamilyId(), "CLAIM_MISMATCH", now);
            throw invalidRefreshToken();
        }

        if (storedToken.isRevoked()) {
            revokeFamily(storedToken.getFamilyId(), "TOKEN_REUSE_DETECTED", now);
            log.warn("Refresh token yeniden kullanım girişimi engellendi - userId: {}, familyId: {}",
                    storedToken.getUser().getId(), storedToken.getFamilyId());
            throw new BusinessException(
                    "Refresh token yeniden kullanıldı; oturum güvenlik nedeniyle iptal edildi",
                    "REFRESH_TOKEN_REUSED",
                    HttpStatus.UNAUTHORIZED);
        }

        if (storedToken.isExpired(now)) {
            storedToken.revoke("EXPIRED", now);
            throw invalidRefreshToken();
        }

        User user = storedToken.getUser();
        if (!"ACTIVE".equals(user.getStatus())) {
            revokeFamily(storedToken.getFamilyId(), "USER_INACTIVE", now);
            throw new BusinessException("Hesabınız aktif değil", "ACCOUNT_INACTIVE", HttpStatus.FORBIDDEN);
        }

        UUID replacementId = UUID.randomUUID();
        String replacementToken = jwtService.generateRefreshToken(
                user, replacementId, storedToken.getFamilyId());

        storedToken.rotate(replacementId, now);
        refreshTokenRepository.save(RefreshToken.builder()
                .id(replacementId)
                .user(user)
                .tokenHash(hashToken(replacementToken))
                .familyId(storedToken.getFamilyId())
                .expiresAt(now.plusMillis(jwtService.getRefreshExpirationMs()))
                .build());

        log.info("Refresh token döndürüldü - userId: {}, familyId: {}", user.getId(), storedToken.getFamilyId());
        return authenticationResult(user, replacementToken);
    }

    @Transactional(noRollbackFor = BusinessException.class)
    public void logout(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            return;
        }

        try {
            jwtService.parseRefreshToken(rawRefreshToken);
        } catch (JwtException | IllegalArgumentException exception) {
            return;
        }

        refreshTokenRepository.findByTokenHashForUpdate(hashToken(rawRefreshToken))
                .ifPresent(token -> revokeFamily(token.getFamilyId(), "LOGOUT", Instant.now()));
    }

    private AuthenticationResult issueTokenFamily(User user) {
        UUID familyId = UUID.randomUUID();
        UUID tokenId = UUID.randomUUID();
        String refreshToken = jwtService.generateRefreshToken(user, tokenId, familyId);

        refreshTokenRepository.save(RefreshToken.builder()
                .id(tokenId)
                .user(user)
                .tokenHash(hashToken(refreshToken))
                .familyId(familyId)
                .expiresAt(Instant.now().plusMillis(jwtService.getRefreshExpirationMs()))
                .build());

        return authenticationResult(user, refreshToken);
    }

    private AuthenticationResult authenticationResult(User user, String refreshToken) {
        return new AuthenticationResult(
                AuthDto.TokenResponse.builder()
                        .accessToken(jwtService.generateAccessToken(user))
                        .tokenType("Bearer")
                        .expiresIn(jwtService.getAccessExpirationMs() / 1000)
                        .user(AuthDto.UserInfo.builder()
                                .id(user.getId())
                                .username(user.getUsername())
                                .email(user.getEmail())
                                .firstName(user.getFirstName())
                                .lastName(user.getLastName())
                                .role(user.getRole().name())
                                .build())
                        .build(),
                refreshToken);
    }

    private Claims parseRefreshClaims(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw invalidRefreshToken();
        }
        try {
            return jwtService.parseRefreshToken(rawRefreshToken);
        } catch (JwtException | IllegalArgumentException exception) {
            throw invalidRefreshToken();
        }
    }

    private void validateActiveUser(User user) {
        if (!"ACTIVE".equals(user.getStatus())) {
            throw new BusinessException("Hesabınız aktif değil", "ACCOUNT_INACTIVE", HttpStatus.FORBIDDEN);
        }
    }

    private void revokeFamily(UUID familyId, String reason, Instant now) {
        refreshTokenRepository.revokeActiveFamily(familyId, now, reason);
    }

    private Long extractUserId(Claims claims) {
        Number userId = claims.get("userId", Number.class);
        if (userId == null) {
            throw invalidRefreshToken();
        }
        return userId.longValue();
    }

    private UUID parseUuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (RuntimeException exception) {
            throw invalidRefreshToken();
        }
    }

    static String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 kullanılamıyor", exception);
        }
    }

    private BusinessException invalidCredentials() {
        return new BusinessException(
                "Geçersiz kullanıcı adı veya şifre",
                "INVALID_CREDENTIALS",
                HttpStatus.UNAUTHORIZED);
    }

    private BusinessException invalidRefreshToken() {
        return new BusinessException(INVALID_REFRESH_MESSAGE, "INVALID_REFRESH_TOKEN", HttpStatus.UNAUTHORIZED);
    }

    public record AuthenticationResult(AuthDto.TokenResponse response, String refreshToken) {
    }
}
