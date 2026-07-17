package com.fintech.transaction.controller;

import com.fintech.common.dto.request.TransactionRequest;
import com.fintech.common.dto.response.ApiResponse;
import com.fintech.common.dto.response.TransactionResponse;
import com.fintech.common.audit.AuditAction;
import com.fintech.common.audit.AuditLogEntry;
import com.fintech.common.audit.AuditLogService;
import com.fintech.transaction.entity.TransactionStatusHistory;
import com.fintech.transaction.service.TransactionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/transactions")
@RequiredArgsConstructor
public class TransactionController {

    private final TransactionService transactionService;
    private final AuditLogService auditLogService;

    /**
     * Yeni işlem oluştur → Kafka pipeline başlar
     * Gateway'den gelen X-User-Id ve X-User-Name header'ları kullanılır
     */
    @PostMapping
    public ResponseEntity<ApiResponse<TransactionResponse>> createTransaction(
            @Valid @RequestBody TransactionRequest request,
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader("X-User-Name") String username,
            @RequestHeader("X-User-Role") String role,
            HttpServletRequest httpRequest) {

        TransactionResponse response = transactionService.createTransaction(request, userId, username, role);
        audit(userId, username, role, AuditAction.TRANSACTION_CREATED, "TRANSACTION", response.getTransactionId(),
                "POST", httpRequest, "type=" + response.getType());
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "İşlem oluşturuldu, pipeline başlatıldı"));
    }

    /**
     * İşlem detayını getir
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<TransactionResponse>> getTransaction(
            @PathVariable UUID id,
            @RequestHeader("X-User-Id") Long authenticatedUserId,
            @RequestHeader("X-User-Name") String username,
            @RequestHeader("X-User-Role") String role,
            HttpServletRequest request) {
        TransactionResponse transaction = transactionService.getTransactionById(id, authenticatedUserId, role);
        audit(authenticatedUserId, username, role, AuditAction.TRANSACTION_VIEWED, "TRANSACTION", id.toString(),
                "GET", request, null);
        return ResponseEntity.ok(ApiResponse.success(transaction));
    }

    /**
     * Hesaba ait işlemleri listele
     */
    @GetMapping("/account/{accountId}")
    public ResponseEntity<ApiResponse<Page<TransactionResponse>>> getTransactionsByAccount(
            @PathVariable Long accountId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestHeader("X-User-Id") Long authenticatedUserId,
            @RequestHeader("X-User-Name") String username,
            @RequestHeader("X-User-Role") String role,
            HttpServletRequest request) {

        Page<TransactionResponse> transactions = transactionService
                .getTransactionsByAccount(accountId, authenticatedUserId, role, PageRequest.of(page, size));
        audit(authenticatedUserId, username, role, AuditAction.TRANSACTION_LIST_VIEWED, "ACCOUNT", accountId.toString(),
                "GET", request, "resultCount=" + transactions.getNumberOfElements());
        return ResponseEntity.ok(ApiResponse.success(transactions));
    }

    /**
     * İşlem durum geçmişini getir (pipeline'daki her aşama)
     */
    @GetMapping("/{id}/history")
    public ResponseEntity<ApiResponse<List<TransactionStatusHistory>>> getTransactionHistory(
            @PathVariable UUID id,
            @RequestHeader("X-User-Id") Long authenticatedUserId,
            @RequestHeader("X-User-Name") String username,
            @RequestHeader("X-User-Role") String role,
            HttpServletRequest request) {
        List<TransactionStatusHistory> history = transactionService.getTransactionHistory(id, authenticatedUserId, role);
        audit(authenticatedUserId, username, role, AuditAction.TRANSACTION_HISTORY_VIEWED, "TRANSACTION", id.toString(),
                "GET", request, "statusCount=" + history.size());
        return ResponseEntity.ok(ApiResponse.success(history));
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
                .serviceName("transaction-service")
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
}
