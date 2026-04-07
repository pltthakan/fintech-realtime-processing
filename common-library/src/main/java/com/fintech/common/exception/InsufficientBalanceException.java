package com.fintech.common.exception;

import org.springframework.http.HttpStatus;

public class InsufficientBalanceException extends BusinessException {

    public InsufficientBalanceException(Long accountId) {
        super(
            String.format("Hesap %d için yetersiz bakiye", accountId),
            "INSUFFICIENT_BALANCE",
            HttpStatus.UNPROCESSABLE_ENTITY
        );
    }
}
