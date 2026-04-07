package com.fintech.reporting.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "completed_transactions")
public class CompletedTransaction {

    @Id
    private String id;
    private String transactionId;
    private Long sourceAccountId;
    private Long targetAccountId;
    private String sourceAccountNumber;
    private String targetAccountNumber;
    private BigDecimal amount;
    private String currency;
    private String type;
    private String status;
    private Short fraudScore;
    private Boolean isSuspicious;
    private Boolean isBlocked;
    private String description;
    private String referenceNumber;
    private Long userId;
    private String username;
    private String fraudCheckMessage;
    private Instant rawTimestamp;
    private Instant validatedTimestamp;
    private Instant checkedTimestamp;
    private Instant processedTimestamp;
    private Instant completedTimestamp;
    private Long totalProcessingTimeMs;
    private Map<String, Object> metadata;
}
