package com.fintech.common.event;

import com.fintech.common.enums.Currency;
import com.fintech.common.enums.TransactionStatus;
import com.fintech.common.enums.TransactionType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

/**
 * Kafka Pipeline boyunca akan ana event modeli.
 *
 * Akış: Transaction Service (A) → Fraud Service (B) → Account Service (C)
 *       → Notification Service (D) → Kafka Connect → MongoDB
 *
 * Her servis bu event'i zenginleştirerek bir sonraki topic'e yazar.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionEvent {

    // ── Temel İşlem Bilgileri ──
    private String transactionId;          // UUID
    private Long sourceAccountId;
    private Long targetAccountId;
    private String sourceAccountNumber;
    private String targetAccountNumber;
    private BigDecimal amount;
    private Currency currency;
    private TransactionType type;
    private TransactionStatus status;
    private String description;
    private String referenceNumber;
    private String idempotencyKey;

    // ── Kullanıcı Bilgileri ──
    private Long userId;
    private String username;
    private String initiatorRole;

    // ── Fraud Kontrol Sonuçları (B servisi doldurur) ──
    private Short fraudScore;
    private Boolean isSuspicious;
    private Boolean isBlocked;
    private String fraudCheckMessage;

    // ── Pipeline Metadata (her servis kendi timestamp'ini ekler) ──
    private Instant rawTimestamp;           // A servisi
    private Instant validatedTimestamp;     // A servisi
    private Instant checkedTimestamp;       // B servisi
    private Instant processedTimestamp;     // C servisi
    private Instant completedTimestamp;     // D servisi
    private Long totalProcessingTimeMs;

    // ── Hata Bilgileri ──
    private String errorMessage;
    private String failedAtService;

    // ── Ekstra Bilgiler ──
    private Map<String, Object> metadata;
}
