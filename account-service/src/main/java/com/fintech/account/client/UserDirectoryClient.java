package com.fintech.account.client;

import com.fintech.common.dto.internal.UserSnapshot;
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

/** Account Service'in User verisine tablo JOIN'i yerine kullandığı servis istemcisi. */
@Component
public class UserDirectoryClient {

    private final RestClient userClient;

    public UserDirectoryClient(
            RestClient.Builder builder,
            @Value("${clients.user-service.base-url}") String userServiceUrl) {
        this.userClient = builder.baseUrl(userServiceUrl).build();
    }

    public UserSnapshot getUser(Long userId) {
        try {
            ApiResponse<UserSnapshot> response = userClient.get()
                    .uri("/api/v1/internal/users/{userId}", userId)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {
                    });
            if (response == null || response.getData() == null) {
                throw unavailable();
            }
            return response.getData();
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().value() == 404) {
                throw new ResourceNotFoundException("Kullanıcı", "id", userId);
            }
            throw unavailable();
        } catch (RestClientException exception) {
            throw unavailable();
        }
    }

    private BusinessException unavailable() {
        return new BusinessException(
                "Kullanıcı servisine şu anda ulaşılamıyor",
                "USER_SERVICE_UNAVAILABLE",
                HttpStatus.SERVICE_UNAVAILABLE);
    }
}
