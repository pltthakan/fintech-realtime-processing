package com.fintech.paymentrail.dto;

import com.fintech.common.enums.Currency;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class BeneficiaryResolveRequest {
    @NotNull
    private Long sourceAccountId;

    @NotBlank
    @Size(max = 34)
    private String iban;

    @Size(max = 120)
    private String beneficiaryName;

    @NotNull
    @DecimalMin("0.01")
    @Digits(integer = 13, fraction = 2)
    private BigDecimal amount;

    @NotNull
    private Currency currency;
}
