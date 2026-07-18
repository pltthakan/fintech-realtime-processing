package com.fintech.paymentrail.dto;

import com.fintech.common.enums.Currency;
import com.fintech.common.enums.TransferRail;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class BeneficiaryResolveResponse {
    private String maskedIban;
    private String maskedBeneficiaryName;
    private String bankCode;
    private Currency currency;
    private TransferRail rail;
    private boolean internal;
}
