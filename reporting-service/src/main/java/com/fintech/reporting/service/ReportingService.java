package com.fintech.reporting.service;

import com.fintech.reporting.dto.CompletedTransaction;
import com.fintech.reporting.dto.DashboardSummary;
import com.fintech.reporting.repository.CompletedTransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReportingService {

    private final CompletedTransactionRepository transactionRepository;

    /**
     * Dashboard özet bilgileri
     */
    public DashboardSummary getDashboardSummary() {
        List<CompletedTransaction> allTransactions = transactionRepository.findAll();

        long total = allTransactions.size();
        long completed = allTransactions.stream().filter(t -> "COMPLETED".equals(t.getStatus())).count();
        long failed = allTransactions.stream().filter(t -> "FAILED".equals(t.getStatus())).count();
        long blocked = allTransactions.stream().filter(t -> "BLOCKED".equals(t.getStatus())).count();
        long suspicious = allTransactions.stream().filter(t -> Boolean.TRUE.equals(t.getIsSuspicious())).count();

        BigDecimal totalVolume = allTransactions.stream()
                .filter(t -> t.getAmount() != null)
                .map(CompletedTransaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal averageAmount = total > 0
                ? totalVolume.divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        Double avgProcessingTime = allTransactions.stream()
                .filter(t -> t.getTotalProcessingTimeMs() != null)
                .mapToLong(CompletedTransaction::getTotalProcessingTimeMs)
                .average().orElse(0.0);

        // Tipe göre dağılım
        Map<String, Long> byType = allTransactions.stream()
                .filter(t -> t.getType() != null)
                .collect(Collectors.groupingBy(CompletedTransaction::getType, Collectors.counting()));

        // Para birimine göre dağılım
        Map<String, Long> byCurrency = allTransactions.stream()
                .filter(t -> t.getCurrency() != null)
                .collect(Collectors.groupingBy(CompletedTransaction::getCurrency, Collectors.counting()));

        // Para birimine göre hacim
        Map<String, BigDecimal> volumeByCurrency = new HashMap<>();
        allTransactions.stream()
                .filter(t -> t.getCurrency() != null && t.getAmount() != null)
                .forEach(t -> volumeByCurrency.merge(t.getCurrency(), t.getAmount(), BigDecimal::add));

        // Son 10 işlem
        List<CompletedTransaction> recent = transactionRepository.findAll(
                        PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "completedTimestamp")))
                .getContent();

        return DashboardSummary.builder()
                .totalTransactions(total)
                .completedTransactions(completed)
                .failedTransactions(failed)
                .blockedTransactions(blocked)
                .totalVolume(totalVolume)
                .averageAmount(averageAmount)
                .averageProcessingTimeMs(avgProcessingTime)
                .suspiciousTransactions(suspicious)
                .transactionsByType(byType)
                .transactionsByCurrency(byCurrency)
                .volumeByCurrency(volumeByCurrency)
                .recentTransactions(recent)
                .build();
    }

    /**
     * Kullanıcı bazlı işlem geçmişi
     */
    public Page<CompletedTransaction> getTransactionsByUser(Long userId, int page, int size) {
        return transactionRepository.findByUserIdOrderByCompletedTimestampDesc(
                userId, PageRequest.of(page, size));
    }

    /**
     * Hesap bazlı işlem geçmişi. Transferin hem kaynak hem hedef hesabı rapora dahil edilir.
     */
    public Page<CompletedTransaction> getTransactionsByAccount(Long accountId, int page, int size) {
        return transactionRepository.findBySourceAccountIdOrTargetAccountIdOrderByCompletedTimestampDesc(
                accountId, accountId, PageRequest.of(page, size));
    }

    /**
     * Tarih aralığına göre işlemler
     */
    public List<CompletedTransaction> getTransactionsByDateRange(LocalDate startDate, LocalDate endDate) {
        Instant start = startDate.atStartOfDay(ZoneId.of("Europe/Istanbul")).toInstant();
        Instant end = endDate.plusDays(1).atStartOfDay(ZoneId.of("Europe/Istanbul")).toInstant();
        // Kafka Connect tarih alanlarını ISO-8601 metin olarak saklar. Bu biçim kronolojik olarak
        // sıralanabildiği için UTC sınırlarıyla metin karşılaştırması doğru tarih aralığını verir.
        return transactionRepository.findByCompletedTimestampRange(
                start.toString(), end.toString());
    }

    /**
     * Şüpheli işlemler listesi
     */
    public List<CompletedTransaction> getSuspiciousTransactions() {
        return transactionRepository.findByIsSuspiciousTrue();
    }

    /**
     * Engellenen işlemler listesi
     */
    public List<CompletedTransaction> getBlockedTransactions() {
        return transactionRepository.findByIsBlockedTrue();
    }

    /**
     * Tek işlem detayı
     */
    public CompletedTransaction getTransactionById(String transactionId) {
        return transactionRepository.findByTransactionId(transactionId)
                .orElse(null);
    }
}
