package com.fintech.paymentrail.controller;

import com.fintech.common.dto.response.ApiResponse;
import com.fintech.paymentrail.dto.BeneficiaryResolveRequest;
import com.fintech.paymentrail.dto.BeneficiaryResolveResponse;
import com.fintech.paymentrail.service.BeneficiaryResolutionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/payment-rails")
@RequiredArgsConstructor
public class PaymentRailController {

    private final BeneficiaryResolutionService beneficiaryResolutionService;

    @PostMapping("/beneficiaries/resolve")
    public ResponseEntity<ApiResponse<BeneficiaryResolveResponse>> resolveBeneficiary(
            @Valid @RequestBody BeneficiaryResolveRequest request,
            @RequestHeader("X-User-Id") Long userId) {
        return ResponseEntity.ok(ApiResponse.success(beneficiaryResolutionService.resolve(request, userId)));
    }
}
