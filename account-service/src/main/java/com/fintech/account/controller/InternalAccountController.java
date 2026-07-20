package com.fintech.account.controller;

import com.fintech.account.dto.InternalBeneficiaryResponse;
import com.fintech.account.service.AccountService;
import com.fintech.common.dto.internal.AccountSnapshot;
import com.fintech.common.dto.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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

    @GetMapping("/{accountId}")
    public ResponseEntity<ApiResponse<AccountSnapshot>> getAccount(@PathVariable Long accountId) {
        return ResponseEntity.ok(ApiResponse.success(accountService.getInternalAccount(accountId)));
    }

    @GetMapping("/iban/{iban}")
    public ResponseEntity<ApiResponse<AccountSnapshot>> getAccountByIban(@PathVariable String iban) {
        return ResponseEntity.ok(ApiResponse.success(accountService.getInternalAccountByIban(iban)));
    }

    @GetMapping("/owners/{userId}/ids")
    public ResponseEntity<ApiResponse<List<Long>>> getAccountIdsByOwner(@PathVariable Long userId) {
        return ResponseEntity.ok(ApiResponse.success(accountService.getInternalAccountIdsByUser(userId)));
    }
}
