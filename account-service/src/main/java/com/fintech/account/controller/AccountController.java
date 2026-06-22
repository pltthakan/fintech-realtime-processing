package com.fintech.account.controller;

import com.fintech.account.entity.Account;
import com.fintech.account.service.AccountService;
import com.fintech.common.dto.response.ApiResponse;
import com.fintech.common.enums.AccountType;
import com.fintech.common.enums.Currency;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/v1/accounts")
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<Account>> getAccountById(
            @PathVariable Long id,
            @RequestHeader("X-User-Id") Long authenticatedUserId,
            @RequestHeader("X-User-Role") String role) {
        return ResponseEntity.ok(ApiResponse.success(
                accountService.getAccountById(id, authenticatedUserId, isAdministrator(role))));
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<ApiResponse<List<Account>>> getAccountsByUserId(
            @PathVariable Long userId,
            @RequestHeader("X-User-Id") Long authenticatedUserId,
            @RequestHeader("X-User-Role") String role) {
        return ResponseEntity.ok(ApiResponse.success(
                accountService.getAccountsByUserId(userId, authenticatedUserId, isAdministrator(role))));
    }

    @GetMapping("/number/{accountNumber}")
    public ResponseEntity<ApiResponse<Account>> getAccountByNumber(
            @PathVariable String accountNumber,
            @RequestHeader("X-User-Id") Long authenticatedUserId,
            @RequestHeader("X-User-Role") String role) {
        return ResponseEntity.ok(ApiResponse.success(
                accountService.getAccountByNumber(accountNumber, authenticatedUserId, isAdministrator(role))));
    }

    /**
     * Yeni hesap oluştur.
     * Gateway'den gelen X-User-Id header'ı kullanılır.
     */
    @PostMapping
    public ResponseEntity<ApiResponse<Account>> createAccount(
            @Valid @RequestBody CreateAccountRequest request,
            @RequestHeader("X-User-Id") Long userId) {

        Account account = accountService.createAccount(
                userId,
                request.getAccountName(),
                request.getAccountType() != null ? request.getAccountType() : AccountType.CHECKING,
                request.getCurrency() != null ? request.getCurrency() : Currency.TRY,
                request.getInitialBalance() != null ? request.getInitialBalance() : BigDecimal.ZERO
        );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(account, "Hesap başarıyla oluşturuldu"));
    }

    private boolean isAdministrator(String role) {
        return "ADMIN".equals(role);
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreateAccountRequest {
        @Size(max = 100, message = "Hesap adı en fazla 100 karakter olabilir")
        private String accountName;

        private AccountType accountType;

        private Currency currency;

        private BigDecimal initialBalance;
    }
}
