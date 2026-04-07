package com.fintech.transaction.controller;

import com.fintech.common.dto.request.TransactionRequest;
import com.fintech.common.dto.response.ApiResponse;
import com.fintech.common.dto.response.TransactionResponse;
import com.fintech.transaction.entity.TransactionStatusHistory;
import com.fintech.transaction.service.TransactionService;
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

    /**
     * Yeni işlem oluştur → Kafka pipeline başlar
     * Gateway'den gelen X-User-Id ve X-User-Name header'ları kullanılır
     */
    @PostMapping
    public ResponseEntity<ApiResponse<TransactionResponse>> createTransaction(
            @Valid @RequestBody TransactionRequest request,
            @RequestHeader(value = "X-User-Id", defaultValue = "0") Long userId,
            @RequestHeader(value = "X-User-Name", defaultValue = "system") String username) {

        TransactionResponse response = transactionService.createTransaction(request, userId, username);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "İşlem oluşturuldu, pipeline başlatıldı"));
    }

    /**
     * İşlem detayını getir
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<TransactionResponse>> getTransaction(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(transactionService.getTransactionById(id)));
    }

    /**
     * Hesaba ait işlemleri listele
     */
    @GetMapping("/account/{accountId}")
    public ResponseEntity<ApiResponse<Page<TransactionResponse>>> getTransactionsByAccount(
            @PathVariable Long accountId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Page<TransactionResponse> transactions = transactionService
                .getTransactionsByAccount(accountId, PageRequest.of(page, size));
        return ResponseEntity.ok(ApiResponse.success(transactions));
    }

    /**
     * İşlem durum geçmişini getir (pipeline'daki her aşama)
     */
    @GetMapping("/{id}/history")
    public ResponseEntity<ApiResponse<List<TransactionStatusHistory>>> getTransactionHistory(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(transactionService.getTransactionHistory(id)));
    }
}
