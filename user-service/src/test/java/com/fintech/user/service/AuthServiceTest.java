package com.fintech.user.service;

import com.fintech.common.enums.UserRole;
import com.fintech.common.exception.BusinessException;
import com.fintech.user.dto.AuthDto;
import com.fintech.user.entity.RefreshToken;
import com.fintech.user.entity.User;
import com.fintech.user.repository.RefreshTokenRepository;
import com.fintech.user.repository.UserRepository;
import com.fintech.user.security.JwtService;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private RefreshTokenRepository refreshTokenRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtService jwtService;
    @Mock
    private Claims claims;

    private AuthService authService;
    private User user;

    @BeforeEach
    void setUp() {
        authService = new AuthService(
                userRepository, refreshTokenRepository, passwordEncoder, jwtService);
        user = User.builder()
                .id(42L)
                .username("hakan")
                .email("hakan@example.com")
                .passwordHash("hash")
                .role(UserRole.USER)
                .status("ACTIVE")
                .build();
    }

    @Test
    void loginStoresOnlyRefreshTokenHash() {
        when(userRepository.findByUsername("hakan")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("secret", "hash")).thenReturn(true);
        when(jwtService.generateRefreshToken(eq(user), any(UUID.class), any(UUID.class)))
                .thenReturn("raw-refresh-token");
        when(jwtService.generateAccessToken(user)).thenReturn("access-token");
        when(jwtService.getRefreshExpirationMs()).thenReturn(604_800_000L);
        when(jwtService.getAccessExpirationMs()).thenReturn(900_000L);

        AuthService.AuthenticationResult result = authService.login(
                new AuthDto.LoginRequest("hakan", "secret"));

        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(captor.capture());
        assertThat(captor.getValue().getTokenHash())
                .hasSize(64)
                .isNotEqualTo("raw-refresh-token")
                .isEqualTo(AuthService.hashToken("raw-refresh-token"));
        assertThat(result.response().getAccessToken()).isEqualTo("access-token");
        assertThat(result.refreshToken()).isEqualTo("raw-refresh-token");
    }

    @Test
    void refreshRotatesStoredTokenAndReturnsReplacement() {
        UUID tokenId = UUID.randomUUID();
        UUID familyId = UUID.randomUUID();
        RefreshToken stored = token(tokenId, familyId, null);
        mockClaims(tokenId, familyId);
        when(jwtService.parseRefreshToken("old-refresh")).thenReturn(claims);
        when(refreshTokenRepository.findByTokenHashForUpdate(AuthService.hashToken("old-refresh")))
                .thenReturn(Optional.of(stored));
        when(jwtService.generateRefreshToken(eq(user), any(UUID.class), eq(familyId)))
                .thenReturn("new-refresh");
        when(jwtService.generateAccessToken(user)).thenReturn("new-access");
        when(jwtService.getRefreshExpirationMs()).thenReturn(604_800_000L);
        when(jwtService.getAccessExpirationMs()).thenReturn(900_000L);

        AuthService.AuthenticationResult result = authService.refreshToken("old-refresh");

        ArgumentCaptor<RefreshToken> replacementCaptor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(replacementCaptor.capture());
        RefreshToken replacement = replacementCaptor.getValue();
        assertThat(stored.isRevoked()).isTrue();
        assertThat(stored.getRevokedReason()).isEqualTo("ROTATED");
        assertThat(stored.getReplacedByTokenId()).isEqualTo(replacement.getId());
        assertThat(replacement.getFamilyId()).isEqualTo(familyId);
        assertThat(replacement.getTokenHash()).isEqualTo(AuthService.hashToken("new-refresh"));
        assertThat(result.response().getAccessToken()).isEqualTo("new-access");
        assertThat(result.refreshToken()).isEqualTo("new-refresh");
    }

    @Test
    void reusedRotatedTokenRevokesActiveTokenFamily() {
        UUID tokenId = UUID.randomUUID();
        UUID familyId = UUID.randomUUID();
        RefreshToken stored = token(tokenId, familyId, Instant.now().minusSeconds(1));
        mockClaims(tokenId, familyId);
        when(jwtService.parseRefreshToken("reused-refresh")).thenReturn(claims);
        when(refreshTokenRepository.findByTokenHashForUpdate(AuthService.hashToken("reused-refresh")))
                .thenReturn(Optional.of(stored));

        assertThatThrownBy(() -> authService.refreshToken("reused-refresh"))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo("REFRESH_TOKEN_REUSED"));

        verify(refreshTokenRepository).revokeActiveFamily(
                eq(familyId), any(Instant.class), eq("TOKEN_REUSE_DETECTED"));
        verify(jwtService, never()).generateAccessToken(any());
    }

    private RefreshToken token(UUID tokenId, UUID familyId, Instant revokedAt) {
        return RefreshToken.builder()
                .id(tokenId)
                .user(user)
                .tokenHash("hash")
                .familyId(familyId)
                .expiresAt(Instant.now().plusSeconds(3600))
                .revokedAt(revokedAt)
                .build();
    }

    private void mockClaims(UUID tokenId, UUID familyId) {
        when(claims.getId()).thenReturn(tokenId.toString());
        when(claims.get(JwtService.FAMILY_ID_CLAIM, String.class)).thenReturn(familyId.toString());
        when(claims.get("userId", Number.class)).thenReturn(42L);
    }
}
