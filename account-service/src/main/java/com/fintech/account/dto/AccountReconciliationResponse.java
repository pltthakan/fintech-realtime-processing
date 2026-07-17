package com.fintech.account.dto;

import com.fintech.common.enums.Currency;
import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.time.Instant;

@Value
@Builder
public class AccountReconciliationResponse {
    Long accountId;
    Currency currency;
    BigDecimal accountBalance;
    BigDecimal latestLedgerBalance;
    BigDecimal difference;
    boolean reconciled;
    Instant checkedAt;
}
