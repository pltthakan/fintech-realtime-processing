package com.fintech.common.exception;

import org.springframework.http.HttpStatus;

public class FraudDetectedException extends BusinessException {

    public FraudDetectedException(String transactionId, int fraudScore) {
        super(
            String.format("İşlem %s fraud kontrolünde engellendi. Risk skoru: %d", transactionId, fraudScore),
            "FRAUD_DETECTED",
            HttpStatus.FORBIDDEN
        );
    }
}
