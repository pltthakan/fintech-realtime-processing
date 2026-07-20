package com.fintech.transaction.client;

import com.fintech.common.dto.internal.AccountSnapshot;
import com.fintech.common.dto.response.ApiResponse;
import com.fintech.common.exception.BusinessException;
import com.fintech.common.exception.ResourceNotFoundException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.util.List;
import java.util.Optional;

/** Transaction Service'in Account tabloları yerine kullandığı servis istemcisi. */
@Component
public class AccountDirectoryClient {

    private final RestClient accountClient;

    public AccountDirectoryClient(
            RestClient.Builder builder,
            @Value("${clients.account-service.base-url}") String accountServiceUrl) {
        this.accountClient = builder.baseUrl(accountServiceUrl).build();
    }

    public AccountSnapshot getAccount(Long accountId) {
        try {
            ApiResponse<AccountSnapshot> response = accountClient.get()
                    .uri("/api/v1/internal/accounts/{accountId}", accountId)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {
                    });
            return requireData(response);
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().value() == 404) {
                throw new ResourceNotFoundException("Hesap", "id", accountId);
            }
            throw unavailable();
        } catch (RestClientException exception) {
            throw unavailable();
        }
    }

    public Optional<AccountSnapshot> findAccountByIban(String iban) {
        try {
            ApiResponse<AccountSnapshot> response = accountClient.get()
                    .uri("/api/v1/internal/accounts/iban/{iban}", iban)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {
                    });
            return Optional.of(requireData(response));
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().value() == 404) {
                return Optional.empty();
            }
            throw unavailable();
        } catch (RestClientException exception) {
            throw unavailable();
        }
    }

    public List<Long> getAccountIdsByUser(Long userId) {
        try {
            ApiResponse<List<Long>> response = accountClient.get()
                    .uri("/api/v1/internal/accounts/owners/{userId}/ids", userId)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {
                    });
            if (response == null || response.getData() == null) {
                throw unavailable();
            }
            return response.getData();
        } catch (RestClientException exception) {
            throw unavailable();
        }
    }

    private AccountSnapshot requireData(ApiResponse<AccountSnapshot> response) {
        if (response == null || response.getData() == null) {
            throw unavailable();
        }
        return response.getData();
    }

    private BusinessException unavailable() {
        return new BusinessException(
                "Hesap servisine şu anda ulaşılamıyor",
                "ACCOUNT_SERVICE_UNAVAILABLE",
                HttpStatus.SERVICE_UNAVAILABLE);
    }
}
