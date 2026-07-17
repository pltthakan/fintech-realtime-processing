package com.fintech.account.controller;

import com.fintech.account.entity.Account;
import com.fintech.account.service.AccountService;
import com.fintech.account.service.LedgerService;
import com.fintech.account.dto.LedgerEntryResponse;
import com.fintech.account.dto.LedgerTransactionResponse;
import com.fintech.account.dto.AccountReconciliationResponse;
import com.fintech.common.audit.AuditAction;
import com.fintech.common.audit.AuditLogEntry;
import com.fintech.common.audit.AuditLogService;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/accounts")
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;
    private final LedgerService ledgerService;
    private final AuditLogService auditLogService;

    @GetMapping("/{accountId}/ledger")
    public ResponseEntity<ApiResponse<Page<LedgerEntryResponse>>> getAccountLedger(
            @PathVariable Long accountId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader("X-User-Name") String username,
            @RequestHeader("X-User-Role") String role,
            HttpServletRequest request) {
        accountService.getAccountById(accountId, userId, isAdministrator(role));
        Page<LedgerEntryResponse> entries = ledgerService.getAccountEntries(
                accountId, PageRequest.of(page, Math.min(Math.max(size, 1), 100)));
        audit(userId, username, role, AuditAction.ACCOUNT_VIEWED, "LEDGER", accountId.toString(),
                "GET", request, "entryCount=" + entries.getNumberOfElements());
        return ResponseEntity.ok(ApiResponse.success(entries));
    }

    @GetMapping("/{accountId}/reconciliation")
    public ResponseEntity<ApiResponse<AccountReconciliationResponse>> reconcileAccount(
            @PathVariable Long accountId,
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader("X-User-Name") String username,
            @RequestHeader("X-User-Role") String role,
            HttpServletRequest request) {
        Account account = accountService.getAccountById(accountId, userId, isAdministrator(role));
        AccountReconciliationResponse result = ledgerService.reconcile(account);
        audit(userId, username, role, AuditAction.ACCOUNT_VIEWED, "RECONCILIATION", accountId.toString(),
                "GET", request, "reconciled=" + result.isReconciled());
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @GetMapping("/ledger/transactions/{transactionId}")
    public ResponseEntity<ApiResponse<LedgerTransactionResponse>> getLedgerTransaction(
            @PathVariable UUID transactionId,
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader("X-User-Name") String username,
            @RequestHeader("X-User-Role") String role,
            HttpServletRequest request) {
        LedgerTransactionResponse journal = ledgerService.getTransaction(
                transactionId, userId, isAdministrator(role));
        audit(userId, username, role, AuditAction.TRANSACTION_VIEWED, "LEDGER_TRANSACTION",
                transactionId.toString(), "GET", request, "balanced=" + journal.isBalanced());
        return ResponseEntity.ok(ApiResponse.success(journal));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<Account>> getAccountById(
            @PathVariable Long id,
            @RequestHeader("X-User-Id") Long authenticatedUserId,
            @RequestHeader("X-User-Name") String username,
            @RequestHeader("X-User-Role") String role,
            HttpServletRequest request) {
        Account account = accountService.getAccountById(id, authenticatedUserId, isAdministrator(role));
        audit(authenticatedUserId, username, role, AuditAction.ACCOUNT_VIEWED, "ACCOUNT", id.toString(),
                "GET", request, null);
        return ResponseEntity.ok(ApiResponse.success(account));
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<ApiResponse<List<Account>>> getAccountsByUserId(
            @PathVariable Long userId,
            @RequestHeader("X-User-Id") Long authenticatedUserId,
            @RequestHeader("X-User-Name") String username,
            @RequestHeader("X-User-Role") String role,
            HttpServletRequest request) {
        List<Account> accounts = accountService.getAccountsByUserId(userId, authenticatedUserId, isAdministrator(role));
        audit(authenticatedUserId, username, role, AuditAction.ACCOUNT_LIST_VIEWED, "USER", userId.toString(),
                "GET", request, "accountCount=" + accounts.size());
        return ResponseEntity.ok(ApiResponse.success(accounts));
    }

    @GetMapping("/number/{accountNumber}")
    public ResponseEntity<ApiResponse<Account>> getAccountByNumber(
            @PathVariable String accountNumber,
            @RequestHeader("X-User-Id") Long authenticatedUserId,
            @RequestHeader("X-User-Name") String username,
            @RequestHeader("X-User-Role") String role,
            HttpServletRequest request) {
        Account account = accountService.getAccountByNumber(accountNumber, authenticatedUserId, isAdministrator(role));
        audit(authenticatedUserId, username, role, AuditAction.ACCOUNT_VIEWED, "ACCOUNT", account.getId().toString(),
                "GET", request, "lookup=accountNumber");
        return ResponseEntity.ok(ApiResponse.success(account));
    }

    /**
     * Yeni hesap oluştur.
     * Gateway'den gelen X-User-Id header'ı kullanılır.
     */
    @PostMapping
    public ResponseEntity<ApiResponse<Account>> createAccount(
            @Valid @RequestBody CreateAccountRequest request,
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader("X-User-Name") String username,
            @RequestHeader("X-User-Role") String role,
            HttpServletRequest httpRequest) {

        Account account = accountService.createAccount(
                userId,
                request.getAccountName(),
                request.getAccountType() != null ? request.getAccountType() : AccountType.CHECKING,
                request.getCurrency() != null ? request.getCurrency() : Currency.TRY
        );

        audit(userId, username, role, AuditAction.ACCOUNT_CREATED, "ACCOUNT", account.getId().toString(),
                "POST", httpRequest, "type=" + account.getAccountType() + ",currency=" + account.getCurrency());

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(account, "Hesap başarıyla oluşturuldu"));
    }

    private boolean isAdministrator(String role) {
        return "ADMIN".equals(role);
    }

    private void audit(Long userId, String username, String role, AuditAction action, String resourceType,
                       String resourceId, String httpMethod, HttpServletRequest request, String details) {
        auditLogService.record(AuditLogEntry.builder()
                .actorUserId(userId)
                .actorUsername(username)
                .actorRole(role)
                .action(action)
                .resourceType(resourceType)
                .resourceId(resourceId)
                .serviceName("account-service")
                .httpMethod(httpMethod)
                .clientIp(resolveClientIp(request))
                .details(details)
                .build());
    }

    private String resolveClientIp(HttpServletRequest request) {
        String clientIp = request.getHeader("X-Client-Ip");
        if (clientIp != null && !clientIp.isBlank()) {
            return clientIp;
        }
        return request.getRemoteAddr();
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
    }
}
