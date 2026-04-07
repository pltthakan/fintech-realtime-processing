package com.fintech.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Tüm iş mantığı hatalarının temel sınıfı.
 * Her mikro servis bu sınıftan türetir.
 */
@Getter
public class BusinessException extends RuntimeException {

    private final String errorCode;
    private final HttpStatus httpStatus;

    public BusinessException(String message, String errorCode, HttpStatus httpStatus) {
        super(message);
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
    }

    public BusinessException(String message, String errorCode) {
        this(message, errorCode, HttpStatus.BAD_REQUEST);
    }
}
