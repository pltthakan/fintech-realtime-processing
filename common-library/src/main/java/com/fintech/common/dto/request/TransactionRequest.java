package com.fintech.common.dto.request;

import com.fintech.common.enums.Currency;
import com.fintech.common.enums.TransactionType;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Yeni işlem oluşturma isteği.
 * Frontend → API Gateway → Transaction Service (A)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionRequest {

    @NotNull(message = "Gönderen hesap ID zorunludur")
    private Long sourceAccountId;

    private Long targetAccountId;

    @NotNull(message = "Tutar zorunludur")
    @DecimalMin(value = "0.01", message = "Tutar 0'dan büyük olmalıdır")
    @Digits(integer = 13, fraction = 2, message = "Geçersiz tutar formatı")
    private BigDecimal amount;

    @NotNull(message = "Para birimi zorunludur")
    private Currency currency;

    @NotNull(message = "İşlem tipi zorunludur")
    private TransactionType type;

    @Size(max = 255, message = "Açıklama en fazla 255 karakter olabilir")
    private String description;

    /** İdempotent işlemler için benzersiz anahtar */
    private String idempotencyKey;
}
