package com.fintech.gateway.filter;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtUtilTest {

    private static final String ACCESS_SECRET = "gateway-access-secret-for-tests-at-least-32-bytes";
    private static final String ISSUER = "fintech-user-service";
    private static final String AUDIENCE = "fintech-api";

    private JwtUtil jwtUtil;
    private SecretKey key;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil();
        ReflectionTestUtils.setField(jwtUtil, "accessSecret", ACCESS_SECRET);
        ReflectionTestUtils.setField(jwtUtil, "issuer", ISSUER);
        ReflectionTestUtils.setField(jwtUtil, "audience", AUDIENCE);
        jwtUtil.init();
        key = Keys.hmacShaKeyFor(ACCESS_SECRET.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void acceptsAccessTokenWithExpectedIssuerAndAudience() {
        Claims claims = jwtUtil.parseAccessToken(token("access", ISSUER, AUDIENCE));

        assertThat(jwtUtil.extractUserId(claims)).isEqualTo(42L);
        assertThat(jwtUtil.extractUsername(claims)).isEqualTo("hakan");
        assertThat(jwtUtil.extractRole(claims)).isEqualTo("USER");
    }

    @Test
    void rejectsRefreshTokenEvenWhenSignedWithAccessKey() {
        String refreshToken = token("refresh", ISSUER, AUDIENCE);

        assertThatThrownBy(() -> jwtUtil.parseAccessToken(refreshToken))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void rejectsTokenForDifferentAudience() {
        String wrongAudienceToken = token("access", ISSUER, "another-api");

        assertThatThrownBy(() -> jwtUtil.parseAccessToken(wrongAudienceToken))
                .isInstanceOf(JwtException.class);
    }

    private String token(String tokenType, String issuer, String audience) {
        Instant now = Instant.now();
        return Jwts.builder()
                .issuer(issuer)
                .audience().add(audience).and()
                .subject("hakan")
                .id(UUID.randomUUID().toString())
                .claims(Map.of(
                        "userId", 42L,
                        "role", "USER",
                        "tokenType", tokenType
                ))
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(900)))
                .signWith(key)
                .compact();
    }
}
