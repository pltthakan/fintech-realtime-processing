package com.fintech.reporting.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardSummary {

    private long totalTransactions;
    private long completedTransactions;
    private long failedTransactions;
    private long blockedTransactions;
    private BigDecimal totalVolume;
    private BigDecimal averageAmount;
    private Double averageProcessingTimeMs;
    private long suspiciousTransactions;
    private Map<String, Long> transactionsByType;
    private Map<String, Long> transactionsByCurrency;
    private Map<String, BigDecimal> volumeByCurrency;
    private List<CompletedTransaction> recentTransactions;
}
