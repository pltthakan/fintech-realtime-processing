package com.fintech.user.service;

import com.fintech.common.exception.BusinessException;
import com.fintech.common.exception.ResourceNotFoundException;
import com.fintech.user.dto.AuthDto;
import com.fintech.user.entity.User;
import com.fintech.user.repository.UserRepository;
import com.fintech.user.security.JwtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    @Transactional
    public AuthDto.TokenResponse register(AuthDto.RegisterRequest request) {
        // Kullanıcı adı ve email kontrolü
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new BusinessException("Bu kullanıcı adı zaten kullanılıyor", "USERNAME_EXISTS", HttpStatus.CONFLICT);
        }
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new BusinessException("Bu email adresi zaten kullanılıyor", "EMAIL_EXISTS", HttpStatus.CONFLICT);
        }

        // Kullanıcı oluştur
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

        return generateTokenResponse(user);
    }

    @Transactional
    public AuthDto.TokenResponse login(AuthDto.LoginRequest request) {
        // Kullanıcıyı bul (username veya email ile)
        User user = userRepository.findByUsername(request.getUsernameOrEmail())
                .or(() -> userRepository.findByEmail(request.getUsernameOrEmail()))
                .orElseThrow(() -> new BusinessException(
                        "Geçersiz kullanıcı adı veya şifre", "INVALID_CREDENTIALS", HttpStatus.UNAUTHORIZED));

        // Hesap durumu kontrolü
        if (!"ACTIVE".equals(user.getStatus())) {
            throw new BusinessException("Hesabınız aktif değil", "ACCOUNT_INACTIVE", HttpStatus.FORBIDDEN);
        }

        // Şifre kontrolü
        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new BusinessException("Geçersiz kullanıcı adı veya şifre", "INVALID_CREDENTIALS", HttpStatus.UNAUTHORIZED);
        }

        // Son giriş zamanını güncelle
        user.setLastLoginAt(Instant.now());
        userRepository.save(user);

        log.info("Kullanıcı giriş yaptı: {}", user.getUsername());
        return generateTokenResponse(user);
    }

    public AuthDto.TokenResponse refreshToken(AuthDto.RefreshTokenRequest request) {
        if (!jwtService.isTokenValid(request.getRefreshToken())) {
            throw new BusinessException("Geçersiz veya süresi dolmuş refresh token", "INVALID_REFRESH_TOKEN", HttpStatus.UNAUTHORIZED);
        }

        String username = jwtService.extractUsername(request.getRefreshToken());
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("Kullanıcı", "username", username));

        log.info("Token yenilendi: {}", username);
        return generateTokenResponse(user);
    }

    private AuthDto.TokenResponse generateTokenResponse(User user) {
        String accessToken = jwtService.generateAccessToken(user);
        String refreshToken = jwtService.generateRefreshToken(user);

        return AuthDto.TokenResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
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
                .build();
    }
}
