package com.fintech.user.controller;

import com.fintech.common.enums.UserRole;
import com.fintech.user.dto.AuthDto;
import com.fintech.user.service.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class AuthControllerTest {

    private final AuthService authService = mock(AuthService.class);
    private final AuthController controller = new AuthController(
            authService,
            "fintech_refresh",
            "/api/v1/auth",
            true,
            "Strict",
            604_800_000L);

    @Test
    void loginReturnsRefreshTokenOnlyAsSecureHttpOnlyCookie() {
        AuthDto.TokenResponse response = AuthDto.TokenResponse.builder()
                .accessToken("access-token")
                .tokenType("Bearer")
                .expiresIn(900L)
                .user(AuthDto.UserInfo.builder().id(42L).username("hakan").role(UserRole.USER.name()).build())
                .build();
        when(authService.login(any())).thenReturn(
                new AuthService.AuthenticationResult(response, "raw-refresh-token"));

        ResponseEntity<?> result = controller.login(new AuthDto.LoginRequest("hakan", "secret"));

        String setCookie = result.getHeaders().getFirst(HttpHeaders.SET_COOKIE);
        assertThat(setCookie)
                .contains("fintech_refresh=raw-refresh-token")
                .contains("Path=/api/v1/auth")
                .contains("Secure")
                .contains("HttpOnly")
                .contains("SameSite=Strict");
        assertThat(response.toString()).doesNotContain("raw-refresh-token");
    }

    @Test
    void logoutRevokesServerSessionAndClearsCookie() {
        ResponseEntity<?> result = controller.logout("raw-refresh-token");

        verify(authService).logout("raw-refresh-token");
        assertThat(result.getHeaders().getFirst(HttpHeaders.SET_COOKIE))
                .contains("fintech_refresh=")
                .contains("Max-Age=0")
                .contains("HttpOnly");
    }
}
