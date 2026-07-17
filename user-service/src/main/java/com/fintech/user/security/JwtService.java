package com.fintech.user.security;

import com.fintech.user.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
public class JwtService {

    public static final String TOKEN_TYPE_CLAIM = "tokenType";
    public static final String ACCESS_TOKEN_TYPE = "access";
    public static final String REFRESH_TOKEN_TYPE = "refresh";
    public static final String FAMILY_ID_CLAIM = "familyId";

    @Value("${jwt.access-secret}")
    private String accessSecret;

    @Value("${jwt.refresh-secret}")
    private String refreshSecret;

    @Value("${jwt.issuer}")
    private String issuer;

    @Value("${jwt.audience}")
    private String audience;

    @Value("${jwt.access-expiration-ms}")
    private Long accessExpirationMs;

    @Value("${jwt.refresh-expiration-ms}")
    private Long refreshExpirationMs;

    private SecretKey accessKey;
    private SecretKey refreshKey;

    @PostConstruct
    public void init() {
        accessKey = Keys.hmacShaKeyFor(accessSecret.getBytes(StandardCharsets.UTF_8));
        refreshKey = Keys.hmacShaKeyFor(refreshSecret.getBytes(StandardCharsets.UTF_8));
        if (accessSecret.equals(refreshSecret)) {
            log.warn("Access ve refresh JWT secret değerleri aynı. Production ortamında ayrı secret kullanın.");
        }
    }

    public String generateAccessToken(User user) {
        Instant now = Instant.now();
        return Jwts.builder()
                .issuer(issuer)
                .audience().add(audience).and()
                .subject(user.getUsername())
                .id(UUID.randomUUID().toString())
                .claims(Map.of(
                        "userId", user.getId(),
                        "email", user.getEmail(),
                        "role", user.getRole().name(),
                        TOKEN_TYPE_CLAIM, ACCESS_TOKEN_TYPE
                ))
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusMillis(accessExpirationMs)))
                .signWith(accessKey)
                .compact();
    }

    public String generateRefreshToken(User user, UUID tokenId, UUID familyId) {
        Instant now = Instant.now();
        return Jwts.builder()
                .issuer(issuer)
                .audience().add(audience).and()
                .subject(user.getUsername())
                .id(tokenId.toString())
                .claims(Map.of(
                        "userId", user.getId(),
                        FAMILY_ID_CLAIM, familyId.toString(),
                        TOKEN_TYPE_CLAIM, REFRESH_TOKEN_TYPE
                ))
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusMillis(refreshExpirationMs)))
                .signWith(refreshKey)
                .compact();
    }

    public Claims parseAccessToken(String token) {
        return parseToken(token, accessKey, ACCESS_TOKEN_TYPE);
    }

    public Claims parseRefreshToken(String token) {
        return parseToken(token, refreshKey, REFRESH_TOKEN_TYPE);
    }

    private Claims parseToken(String token, SecretKey key, String expectedType) {
        return Jwts.parser()
                .verifyWith(key)
                .requireIssuer(issuer)
                .requireAudience(audience)
                .require(TOKEN_TYPE_CLAIM, expectedType)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public Long getAccessExpirationMs() {
        return accessExpirationMs;
    }

    public Long getRefreshExpirationMs() {
        return refreshExpirationMs;
    }
}
