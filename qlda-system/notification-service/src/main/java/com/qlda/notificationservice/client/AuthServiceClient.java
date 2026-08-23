package com.qlda.notificationservice.client;

import com.qlda.notificationservice.client.dto.AuthUnitResponse;
import com.qlda.notificationservice.client.dto.AuthUserResponse;
import com.qlda.notificationservice.client.dto.InternalApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

@Slf4j
@Component
public class AuthServiceClient {

    private final WebClient webClient;

    public AuthServiceClient(
        WebClient.Builder webClientBuilder,
        @Value("${services.auth-service.base-url:http://auth-service:8081}") String baseUrl,
        @Value("${internal.auth.service-name:notification-service}") String serviceName,
        @Value("${internal.auth.service-token:change-me-in-dev}") String serviceToken
    ) {
        this.webClient = webClientBuilder
            .baseUrl(baseUrl)
            .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + serviceToken)
            .defaultHeader("X-Service-Name", serviceName)
            .build();
    }

    public AuthUserResponse getUserById(Long id) {
        try {
            InternalApiResponse<AuthUserResponse> response = webClient.get()
                .uri("/internal/auth/users/{id}", id)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<InternalApiResponse<AuthUserResponse>>() {})
                .block();
            return requireData(response, "user");
        } catch (WebClientResponseException ex) {
            log.warn("Auth service user lookup failed: status={} body={}",
                ex.getStatusCode(), ex.getResponseBodyAsString());
            throw ex;
        }
    }

    public AuthUnitResponse getUnitById(Integer id) {
        try {
            InternalApiResponse<AuthUnitResponse> response = webClient.get()
                .uri("/internal/auth/units/{id}", id)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<InternalApiResponse<AuthUnitResponse>>() {})
                .block();
            return requireData(response, "unit");
        } catch (WebClientResponseException ex) {
            log.warn("Auth service unit lookup failed: status={} body={}",
                ex.getStatusCode(), ex.getResponseBodyAsString());
            throw ex;
        }
    }

    private <T> T requireData(InternalApiResponse<T> response, String operation) {
        if (response == null || !response.success() || response.data() == null) {
            String message = response == null ? "empty response" : response.message();
            throw new IllegalStateException("Auth service could not return " + operation + ": " + message);
        }
        return response.data();
    }
}
