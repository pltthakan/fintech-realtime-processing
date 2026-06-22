package com.fintech.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Authenticated kullanıcı kaynağın sahibi olmadığında döndürülür.
 */
public class ForbiddenException extends BusinessException {

    public ForbiddenException() {
        super("Bu kaynağa erişim yetkiniz yok", "ACCESS_DENIED", HttpStatus.FORBIDDEN);
    }
}
