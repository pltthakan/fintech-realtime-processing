package com.fintech.account.controller;

import com.fintech.account.dto.InternalBeneficiaryResponse;
import com.fintech.account.service.AccountService;
import com.fintech.common.dto.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/internal/accounts")
@RequiredArgsConstructor
public class InternalAccountController {

    private final AccountService accountService;

    /** Yalnızca Docker servis ağı içinde Payment Rail Service tarafından çağrılır. */
    @GetMapping("/beneficiaries/{iban}")
    public ResponseEntity<ApiResponse<InternalBeneficiaryResponse>> resolveBeneficiary(
            @PathVariable String iban) {
        return ResponseEntity.ok(ApiResponse.success(accountService.resolveInternalBeneficiary(iban)));
    }
}
