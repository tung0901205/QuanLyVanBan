package com.qlda.aiservice.client;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qlda.aiservice.dto.common.InternalApiEnvelope;
import com.qlda.aiservice.exception.AppException;
import com.qlda.aiservice.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class AuthInternalApiClient implements AuthInternalApiService {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final String baseUrl;
    private final String token;
    private final String serviceName;

    public AuthInternalApiClient(
        RestTemplate restTemplate,
        ObjectMapper objectMapper,
        @Value("${services.auth.base-url:http://auth-service:8081}") String baseUrl,
        @Value("${internal.auth.service-token:change-me-in-dev}") String token,
        @Value("${internal.auth.service-name:ai-service}") String serviceName
    ) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.baseUrl = baseUrl;
        this.token = token;
        this.serviceName = serviceName;
    }

    @Override
    public boolean hasSystemStatisticPermission(Long userId) {
        InternalApiEnvelope<?> envelope = exchange(
            baseUrl + "/internal/auth/permissions/check",
            HttpMethod.POST,
            Map.of(
                "userId", userId,
                "maChucNang", "VIEW_REPORTS",
                "permission", "IsView"
            )
        );
        Map<String, Object> raw = objectMapper.convertValue(envelope.data(), new TypeReference<>() {});
        Object allowed = raw == null ? null : raw.get("allowed");
        return allowed instanceof Boolean value && value;
    }

    @Override
    public long getTotalUserCount(Long userId) {
        InternalApiEnvelope<?> envelope = exchange(
            baseUrl + "/internal/auth/statistics/users/count",
            HttpMethod.GET,
            null
        );
        Map<String, Object> raw = objectMapper.convertValue(envelope.data(), new TypeReference<>() {});
        Object count = raw == null ? null : raw.get("count");
        return count instanceof Number number ? number.longValue() : 0L;
    }

    private InternalApiEnvelope<?> exchange(String url, HttpMethod method, Object body) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setAccept(List.of(MediaType.APPLICATION_JSON));
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(token);
            headers.set("X-Service-Name", serviceName);
            HttpEntity<Object> entity = new HttpEntity<>(body, headers);
            ResponseEntity<InternalApiEnvelope> response = restTemplate.exchange(
                url, method, entity, InternalApiEnvelope.class
            );
            InternalApiEnvelope<?> envelope = response.getBody();
            if (envelope == null || !envelope.success()) {
                String message = envelope == null ? "empty response" : envelope.message();
                throw new AppException(
                    ErrorCode.INTERNAL_SERVER_ERROR,
                    HttpStatus.BAD_GATEWAY,
                    "Auth service returned an invalid response: " + message
                );
            }
            return envelope;
        } catch (AppException ex) {
            throw ex;
        } catch (RestClientException ex) {
            log.error("Internal auth-service request failed: method={} url={}", method, url, ex);
            throw new AppException(
                ErrorCode.INTERNAL_SERVER_ERROR,
                HttpStatus.BAD_GATEWAY,
                "Internal auth-service request failed",
                ex
            );
        }
    }
}
