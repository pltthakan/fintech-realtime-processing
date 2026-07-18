package com.fintech.paymentrail.dto;

import lombok.Data;

@Data
public class InternalBeneficiaryResponse {
    private Long accountId;
    private Long userId;
    private String iban;
    private String currency;
    private String status;
    private String beneficiaryName;
}
