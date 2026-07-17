package com.fintech.account.dto;

import com.fintech.common.enums.Currency;
import com.fintech.common.enums.TransactionType;
import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Value
@Builder
public class LedgerTransactionResponse {
    UUID transactionId;
    String referenceNumber;
    TransactionType transactionType;
    Currency currency;
    BigDecimal totalAmount;
    BigDecimal debitTotal;
    BigDecimal creditTotal;
    boolean balanced;
    String status;
    Instant postedAt;
    List<LedgerEntryResponse> entries;
}
