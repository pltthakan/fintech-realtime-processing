package com.fintech.reporting.controller;

import com.fintech.common.dto.response.ApiResponse;
import com.fintech.reporting.dto.CompletedTransaction;
import com.fintech.reporting.dto.DashboardSummary;
import com.fintech.reporting.service.ReportingService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
public class ReportingController {

    private final ReportingService reportingService;

    /** Dashboard özet */
    @GetMapping("/dashboard")
    public ResponseEntity<ApiResponse<DashboardSummary>> getDashboard() {
        return ResponseEntity.ok(ApiResponse.success(reportingService.getDashboardSummary()));
    }

    /** Kullanıcı işlem geçmişi */
    @GetMapping("/user/{userId}")
    public ResponseEntity<ApiResponse<Page<CompletedTransaction>>> getByUser(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(reportingService.getTransactionsByUser(userId, page, size)));
    }

    /** Hesap işlem geçmişi */
    @GetMapping("/account/{accountId}")
    public ResponseEntity<ApiResponse<Page<CompletedTransaction>>> getByAccount(
            @PathVariable Long accountId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(reportingService.getTransactionsByAccount(accountId, page, size)));
    }

    /** Tarih aralığı raporu */
    @GetMapping("/date-range")
    public ResponseEntity<ApiResponse<List<CompletedTransaction>>> getByDateRange(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return ResponseEntity.ok(ApiResponse.success(reportingService.getTransactionsByDateRange(startDate, endDate)));
    }

    /** Şüpheli işlemler */
    @GetMapping("/suspicious")
    public ResponseEntity<ApiResponse<List<CompletedTransaction>>> getSuspicious() {
        return ResponseEntity.ok(ApiResponse.success(reportingService.getSuspiciousTransactions()));
    }

    /** Engellenen işlemler */
    @GetMapping("/blocked")
    public ResponseEntity<ApiResponse<List<CompletedTransaction>>> getBlocked() {
        return ResponseEntity.ok(ApiResponse.success(reportingService.getBlockedTransactions()));
    }

    /** Tek işlem detayı */
    @GetMapping("/transaction/{transactionId}")
    public ResponseEntity<ApiResponse<CompletedTransaction>> getTransaction(@PathVariable String transactionId) {
        return ResponseEntity.ok(ApiResponse.success(reportingService.getTransactionById(transactionId)));
    }
}
