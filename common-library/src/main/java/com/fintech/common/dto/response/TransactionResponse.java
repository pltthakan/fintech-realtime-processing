package com.fintech.common.dto.response;

import com.fintech.common.enums.Currency;
import com.fintech.common.enums.TransactionStatus;
import com.fintech.common.enums.TransactionType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionResponse {

    private String transactionId;
    private Long sourceAccountId;
    private Long targetAccountId;
    private String sourceAccountNumber;
    private String targetAccountNumber;
    private BigDecimal amount;
    private Currency currency;
    private TransactionType type;
    private TransactionStatus status;
    private Short fraudScore;
    private String description;
    private String referenceNumber;
    private Instant createdAt;
    private Instant completedAt;
}
