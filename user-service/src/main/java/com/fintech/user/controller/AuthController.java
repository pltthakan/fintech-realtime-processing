package com.fintech.user.controller;

import com.fintech.common.dto.response.ApiResponse;
import com.fintech.user.dto.AuthDto;
import com.fintech.user.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<AuthDto.TokenResponse>> register(
            @Valid @RequestBody AuthDto.RegisterRequest request) {
        AuthDto.TokenResponse response = authService.register(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Kayıt başarılı"));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthDto.TokenResponse>> login(
            @Valid @RequestBody AuthDto.LoginRequest request) {
        AuthDto.TokenResponse response = authService.login(request);
        return ResponseEntity.ok(ApiResponse.success(response, "Giriş başarılı"));
    }

    @PostMapping("/refresh-token")
    public ResponseEntity<ApiResponse<AuthDto.TokenResponse>> refreshToken(
            @Valid @RequestBody AuthDto.RefreshTokenRequest request) {
        AuthDto.TokenResponse response = authService.refreshToken(request);
        return ResponseEntity.ok(ApiResponse.success(response, "Token yenilendi"));
    }
}
