package com.fintech.account.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class InternalBeneficiaryResponse {
    private Long accountId;
    private Long userId;
    private String iban;
    private String currency;
    private String status;
    private String beneficiaryName;
}
