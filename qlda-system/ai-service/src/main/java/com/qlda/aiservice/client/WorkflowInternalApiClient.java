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
public class WorkflowInternalApiClient implements WorkflowInternalApiService {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final String baseUrl;
    private final String token;
    private final String serviceName;

    public WorkflowInternalApiClient(
        RestTemplate restTemplate,
        ObjectMapper objectMapper,
        @Value("${services.workflow.base-url:http://workflow-service:8083}") String baseUrl,
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
    public long getMyDueSoonDocumentCount(Long userId, int days) {
        InternalApiEnvelope<?> envelope = exchange(
            baseUrl + "/internal/workflows/statistics/my-due-soon-count?userId=" + userId + "&days=" + days,
            HttpMethod.GET,
            null
        );
        return extractCount(envelope);
    }

    @Override
    public long getMyOverdueDocumentCount(Long userId) {
        InternalApiEnvelope<?> envelope = exchange(
            baseUrl + "/internal/workflows/statistics/my-overdue-count?userId=" + userId,
            HttpMethod.GET,
            null
        );
        return extractCount(envelope);
    }

    @Override
    public long getMyPendingDocumentCount(Long userId) {
        InternalApiEnvelope<?> envelope = exchange(
            baseUrl + "/internal/workflows/progress?nguoiXuLyId=" + userId,
            HttpMethod.GET,
            null
        );
        return extractLongField(envelope, "processingTasks");
    }

    @Override
    public long getMyCompletedDocumentCount(Long userId) {
        InternalApiEnvelope<?> envelope = exchange(
            baseUrl + "/internal/workflows/progress?nguoiXuLyId=" + userId,
            HttpMethod.GET,
            null
        );
        return extractLongField(envelope, "completedTasks");
    }

    @Override
    public long getSlaViolationCount() {
        InternalApiEnvelope<?> envelope = exchange(
            baseUrl + "/internal/workflows/sla/violations",
            HttpMethod.GET,
            null
        );
        List<?> items = objectMapper.convertValue(envelope.data(), new TypeReference<List<?>>() {});
        return items == null ? 0L : items.size();
    }

    private long extractCount(InternalApiEnvelope<?> envelope) {
        return extractLongField(envelope, "count");
    }

    private long extractLongField(InternalApiEnvelope<?> envelope, String fieldName) {
        Map<String, Object> raw = objectMapper.convertValue(envelope.data(), new TypeReference<>() {});
        Object count = raw == null ? null : raw.get(fieldName);
        return count instanceof Number number ? number.longValue() : 0L;
    }

    private InternalApiEnvelope<?> exchange(String url, HttpMethod method, Object body) {
        try {
            HttpHeaders headers = internalHeaders();
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
                    "Workflow service returned an invalid response: " + message
                );
            }
            return envelope;
        } catch (AppException ex) {
            throw ex;
        } catch (RestClientException ex) {
            log.error("Internal workflow-service request failed: method={} url={}", method, url, ex);
            throw new AppException(
                ErrorCode.INTERNAL_SERVER_ERROR,
                HttpStatus.BAD_GATEWAY,
                "Internal workflow-service request failed",
                ex
            );
        }
    }

    private HttpHeaders internalHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);
        headers.set("X-Service-Name", serviceName);
        return headers;
    }
}
