package com.fintech.common.exception;

import org.springframework.http.HttpStatus;

public class DuplicateTransactionException extends BusinessException {

    public DuplicateTransactionException(String idempotencyKey) {
        super(
            String.format("Bu işlem zaten mevcut: idempotencyKey = '%s'", idempotencyKey),
            "DUPLICATE_TRANSACTION",
            HttpStatus.CONFLICT
        );
    }
}
