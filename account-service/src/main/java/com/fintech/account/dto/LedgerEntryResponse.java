package com.fintech.account.dto;

import com.fintech.account.entity.LedgerDirection;
import com.fintech.common.enums.Currency;
import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Value
@Builder
public class LedgerEntryResponse {
    UUID id;
    UUID transactionId;
    Long accountId;
    String accountCode;
    LedgerDirection direction;
    BigDecimal amount;
    Currency currency;
    BigDecimal balanceAfter;
    Instant createdAt;
}
