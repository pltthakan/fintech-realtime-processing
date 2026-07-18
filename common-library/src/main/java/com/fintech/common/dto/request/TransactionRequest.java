package com.fintech.common.dto.request;

import com.fintech.common.enums.Currency;
import com.fintech.common.enums.TransactionType;
import com.fintech.common.enums.TransferRail;
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

    private Long sourceAccountId;

    private Long targetAccountId;

    /** IBAN ile başka hesaba transferlerde hedef hesabın kullanıcıya açık kimliği. */
    @Size(max = 34, message = "IBAN en fazla 34 karakter olabilir")
    private String beneficiaryIban;

    @Size(max = 120, message = "Alıcı adı en fazla 120 karakter olabilir")
    private String beneficiaryName;

    /** İstemci değeri yalnızca önizlemedir; backend yönlendirmeyi yeniden hesaplar. */
    private TransferRail transferRail;

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
    @NotBlank(message = "Idempotency anahtarı zorunludur")
    @Size(max = 100, message = "Idempotency anahtarı en fazla 100 karakter olabilir")
    private String idempotencyKey;
}
