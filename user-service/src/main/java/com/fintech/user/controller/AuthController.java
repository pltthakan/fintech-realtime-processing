package com.fintech.user.controller;

import com.fintech.common.dto.response.ApiResponse;
import com.fintech.user.dto.AuthDto;
import com.fintech.user.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final String refreshCookieName;
    private final String refreshCookiePath;
    private final boolean refreshCookieSecure;
    private final String refreshCookieSameSite;
    private final long refreshExpirationMs;

    public AuthController(
            AuthService authService,
            @Value("${auth.refresh-cookie.name}") String refreshCookieName,
            @Value("${auth.refresh-cookie.path}") String refreshCookiePath,
            @Value("${auth.refresh-cookie.secure}") boolean refreshCookieSecure,
            @Value("${auth.refresh-cookie.same-site}") String refreshCookieSameSite,
            @Value("${jwt.refresh-expiration-ms}") long refreshExpirationMs) {
        this.authService = authService;
        this.refreshCookieName = refreshCookieName;
        this.refreshCookiePath = refreshCookiePath;
        this.refreshCookieSecure = refreshCookieSecure;
        this.refreshCookieSameSite = refreshCookieSameSite;
        this.refreshExpirationMs = refreshExpirationMs;
    }

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<AuthDto.TokenResponse>> register(
            @Valid @RequestBody AuthDto.RegisterRequest request) {
        AuthService.AuthenticationResult result = authService.register(request);
        return withRefreshCookie(result, HttpStatus.CREATED, "Kayıt başarılı");
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthDto.TokenResponse>> login(
            @Valid @RequestBody AuthDto.LoginRequest request) {
        AuthService.AuthenticationResult result = authService.login(request);
        return withRefreshCookie(result, HttpStatus.OK, "Giriş başarılı");
    }

    @PostMapping("/refresh-token")
    public ResponseEntity<ApiResponse<AuthDto.TokenResponse>> refreshToken(
            @CookieValue(name = "${auth.refresh-cookie.name}") String refreshToken) {
        AuthService.AuthenticationResult result = authService.refreshToken(refreshToken);
        return withRefreshCookie(result, HttpStatus.OK, "Token yenilendi");
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            @CookieValue(name = "${auth.refresh-cookie.name}", required = false) String refreshToken) {
        authService.logout(refreshToken);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, clearRefreshCookie().toString())
                .body(ApiResponse.success(null, "Çıkış başarılı"));
    }

    private ResponseEntity<ApiResponse<AuthDto.TokenResponse>> withRefreshCookie(
            AuthService.AuthenticationResult result,
            HttpStatus status,
            String message) {
        return ResponseEntity.status(status)
                .header(HttpHeaders.SET_COOKIE, refreshCookie(result.refreshToken()).toString())
                .body(ApiResponse.success(result.response(), message));
    }

    private ResponseCookie refreshCookie(String token) {
        return ResponseCookie.from(refreshCookieName, token)
                .httpOnly(true)
                .secure(refreshCookieSecure)
                .sameSite(refreshCookieSameSite)
                .path(refreshCookiePath)
                .maxAge(Duration.ofMillis(refreshExpirationMs))
                .build();
    }

    private ResponseCookie clearRefreshCookie() {
        return ResponseCookie.from(refreshCookieName, "")
                .httpOnly(true)
                .secure(refreshCookieSecure)
                .sameSite(refreshCookieSameSite)
                .path(refreshCookiePath)
                .maxAge(Duration.ZERO)
                .build();
    }
}
