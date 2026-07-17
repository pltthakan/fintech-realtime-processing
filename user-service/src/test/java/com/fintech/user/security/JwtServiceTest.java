package com.fintech.user.security;

import com.fintech.common.enums.UserRole;
import com.fintech.user.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private static final String ACCESS_SECRET = "access-secret-for-tests-must-be-at-least-32-bytes";
    private static final String REFRESH_SECRET = "refresh-secret-for-tests-must-be-at-least-32-bytes";

    private JwtService jwtService;
    private User user;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService();
        ReflectionTestUtils.setField(jwtService, "accessSecret", ACCESS_SECRET);
        ReflectionTestUtils.setField(jwtService, "refreshSecret", REFRESH_SECRET);
        ReflectionTestUtils.setField(jwtService, "issuer", "fintech-user-service");
        ReflectionTestUtils.setField(jwtService, "audience", "fintech-api");
        ReflectionTestUtils.setField(jwtService, "accessExpirationMs", 900_000L);
        ReflectionTestUtils.setField(jwtService, "refreshExpirationMs", 604_800_000L);
        jwtService.init();

        user = User.builder()
                .id(42L)
                .username("hakan")
                .email("hakan@example.com")
                .passwordHash("not-used")
                .role(UserRole.USER)
                .build();
    }

    @Test
    void accessTokenContainsRequiredSecurityClaims() {
        Claims claims = jwtService.parseAccessToken(jwtService.generateAccessToken(user));

        assertThat(claims.getSubject()).isEqualTo("hakan");
        assertThat(claims.getIssuer()).isEqualTo("fintech-user-service");
        assertThat(claims.getAudience()).containsExactly("fintech-api");
        assertThat(claims.get(JwtService.TOKEN_TYPE_CLAIM, String.class)).isEqualTo("access");
        assertThat(claims.get("userId", Number.class).longValue()).isEqualTo(42L);
        assertThat(claims.get("role", String.class)).isEqualTo("USER");
        assertThat(claims.getId()).isNotBlank();
    }

    @Test
    void refreshTokenContainsFamilyAndCannotBeParsedAsAccessToken() {
        UUID tokenId = UUID.randomUUID();
        UUID familyId = UUID.randomUUID();
        String refreshToken = jwtService.generateRefreshToken(user, tokenId, familyId);

        Claims claims = jwtService.parseRefreshToken(refreshToken);
        assertThat(claims.getId()).isEqualTo(tokenId.toString());
        assertThat(claims.get(JwtService.FAMILY_ID_CLAIM, String.class)).isEqualTo(familyId.toString());
        assertThat(claims.get(JwtService.TOKEN_TYPE_CLAIM, String.class)).isEqualTo("refresh");

        assertThatThrownBy(() -> jwtService.parseAccessToken(refreshToken))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void accessTokenCannotBeUsedAsRefreshToken() {
        String accessToken = jwtService.generateAccessToken(user);

        assertThatThrownBy(() -> jwtService.parseRefreshToken(accessToken))
                .isInstanceOf(JwtException.class);
    }
}
