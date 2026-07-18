package com.fintech.paymentrail.service;

import com.fintech.common.dto.response.ApiResponse;
import com.fintech.common.enums.Currency;
import com.fintech.common.enums.TransferRail;
import com.fintech.common.exception.BusinessException;
import com.fintech.common.util.IbanUtils;
import com.fintech.common.util.TransferRoutingPolicy;
import com.fintech.paymentrail.dto.BeneficiaryResolveRequest;
import com.fintech.paymentrail.dto.BeneficiaryResolveResponse;
import com.fintech.paymentrail.dto.InternalBeneficiaryResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.Arrays;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class BeneficiaryResolutionService {

    private final RestClient accountClient;

    public BeneficiaryResolutionService(
            RestClient.Builder builder,
            @Value("${payment-rail.account-service-url}") String accountServiceUrl) {
        this.accountClient = builder.baseUrl(accountServiceUrl).build();
    }

    public BeneficiaryResolveResponse resolve(BeneficiaryResolveRequest request, Long userId) {
        String iban = IbanUtils.normalize(request.getIban());
        Optional<InternalBeneficiaryResponse> internal = findInternal(iban);
        if (internal.isPresent()) {
            InternalBeneficiaryResponse beneficiary = internal.get();
            if (beneficiary.getUserId().equals(userId)) {
                throw new BusinessException(
                        "Kendi hesabınıza transfer için Kendi Hesaplarım akışını kullanın",
                        "OWN_ACCOUNT_USE_INTERNAL_TRANSFER",
                        HttpStatus.BAD_REQUEST);
            }
            if (!"ACTIVE".equals(beneficiary.getStatus())) {
                throw new BusinessException("Alıcı hesabı aktif değil", "BENEFICIARY_ACCOUNT_INACTIVE");
            }
            if (!request.getCurrency().name().equals(beneficiary.getCurrency())) {
                throw new BusinessException("Alıcı hesabının para birimi eşleşmiyor",
                        "BENEFICIARY_CURRENCY_MISMATCH");
            }
            return response(iban, beneficiary.getBeneficiaryName(), request.getCurrency(), TransferRail.HAVALE, true);
        }

        if (!IbanUtils.isValidTurkishIban(iban)) {
            throw new BusinessException("Geçerli bir Türkiye IBAN'ı girilmelidir",
                    "INVALID_BENEFICIARY_IBAN", HttpStatus.BAD_REQUEST);
        }
        if (request.getCurrency() != Currency.TRY) {
            throw new BusinessException("EFT/FAST simülasyonu yalnızca TRY destekler",
                    "EXTERNAL_TRANSFER_CURRENCY_NOT_SUPPORTED", HttpStatus.BAD_REQUEST);
        }
        if (request.getBeneficiaryName() == null || request.getBeneficiaryName().isBlank()) {
            throw new BusinessException("Harici transferlerde alıcı adı zorunludur",
                    "BENEFICIARY_NAME_REQUIRED", HttpStatus.BAD_REQUEST);
        }

        TransferRail rail = TransferRoutingPolicy.selectExternalRail(request.getAmount(), request.getCurrency());
        return response(iban, request.getBeneficiaryName().trim(), request.getCurrency(), rail, false);
    }

    private Optional<InternalBeneficiaryResponse> findInternal(String iban) {
        try {
            ApiResponse<InternalBeneficiaryResponse> response = accountClient.get()
                    .uri("/api/v1/internal/accounts/beneficiaries/{iban}", iban)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {
                    });
            return response == null ? Optional.empty() : Optional.ofNullable(response.getData());
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().value() == 404) {
                return Optional.empty();
            }
            throw exception;
        }
    }

    private BeneficiaryResolveResponse response(
            String iban, String name, Currency currency, TransferRail rail, boolean internal) {
        return BeneficiaryResolveResponse.builder()
                .maskedIban(IbanUtils.mask(iban))
                .maskedBeneficiaryName(maskName(name))
                .bankCode(IbanUtils.bankCode(iban))
                .currency(currency)
                .rail(rail)
                .internal(internal)
                .build();
    }

    private String maskName(String name) {
        if (name == null || name.isBlank()) {
            return "****";
        }
        return Arrays.stream(name.trim().split("\\s+"))
                .map(part -> part.length() == 1
                        ? part
                        : part.substring(0, 1) + "*".repeat(Math.min(part.length() - 1, 6)))
                .collect(Collectors.joining(" "));
    }
}
