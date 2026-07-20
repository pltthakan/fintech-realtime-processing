package com.fintech.common.dto.internal;

import com.fintech.common.enums.AccountStatus;
import com.fintech.common.enums.Currency;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Salt-okunur servisler arası hesap sözleşmesi. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountSnapshot {
    private Long accountId;
    private Long userId;
    private String accountNumber;
    private Currency currency;
    private AccountStatus status;
}
