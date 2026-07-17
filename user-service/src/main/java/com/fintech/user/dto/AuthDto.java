package com.fintech.user.dto;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

public class AuthDto {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LoginRequest {

        @NotBlank(message = "Kullanıcı adı veya email zorunludur")
        private String usernameOrEmail;

        @NotBlank(message = "Şifre zorunludur")
        private String password;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RegisterRequest {

        @NotBlank(message = "Kullanıcı adı zorunludur")
        @Size(min = 3, max = 50, message = "Kullanıcı adı 3-50 karakter olmalıdır")
        private String username;

        @NotBlank(message = "Email zorunludur")
        @Email(message = "Geçerli bir email adresi giriniz")
        private String email;

        @NotBlank(message = "Şifre zorunludur")
        @Size(min = 8, max = 100, message = "Şifre en az 8 karakter olmalıdır")
        private String password;

        @Size(max = 50)
        private String firstName;

        @Size(max = 50)
        private String lastName;

        @Size(max = 20)
        private String phoneNumber;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TokenResponse {
        private String accessToken;
        private String tokenType;
        private Long expiresIn;
        private UserInfo user;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserInfo {
        private Long id;
        private String username;
        private String email;
        private String firstName;
        private String lastName;
        private String role;
    }

}
