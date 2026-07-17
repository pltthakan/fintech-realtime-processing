package com.fintech.gateway.filter;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;

@Component
public class JwtUtil {

    private static final String TOKEN_TYPE_CLAIM = "tokenType";
    private static final String ACCESS_TOKEN_TYPE = "access";

    @Value("${jwt.access-secret}")
    private String accessSecret;

    @Value("${jwt.issuer}")
    private String issuer;

    @Value("${jwt.audience}")
    private String audience;

    private SecretKey accessKey;

    @PostConstruct
    public void init() {
        accessKey = Keys.hmacShaKeyFor(accessSecret.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Gateway yalnızca access token kabul eder. İmza, expiry, issuer, audience
     * ve tokenType tek parse işleminde doğrulanır.
     */
    public Claims parseAccessToken(String token) {
        return Jwts.parser()
                .verifyWith(accessKey)
                .requireIssuer(issuer)
                .requireAudience(audience)
                .require(TOKEN_TYPE_CLAIM, ACCESS_TOKEN_TYPE)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public String extractUsername(Claims claims) {
        return claims.getSubject();
    }

    public String extractRole(Claims claims) {
        return claims.get("role", String.class);
    }

    public Long extractUserId(Claims claims) {
        Number userId = claims.get("userId", Number.class);
        return userId != null ? userId.longValue() : null;
    }
}
