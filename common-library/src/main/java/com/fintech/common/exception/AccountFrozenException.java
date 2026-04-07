package com.fintech.common.exception;

import org.springframework.http.HttpStatus;

public class AccountFrozenException extends BusinessException {

    public AccountFrozenException(Long accountId) {
        super(
            String.format("Hesap %d dondurulmuş durumda, işlem yapılamaz", accountId),
            "ACCOUNT_FROZEN",
            HttpStatus.FORBIDDEN
        );
    }
}
