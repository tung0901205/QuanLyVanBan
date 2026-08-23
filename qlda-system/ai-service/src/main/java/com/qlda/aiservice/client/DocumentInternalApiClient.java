package com.qlda.aiservice.client;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qlda.aiservice.dto.common.InternalApiEnvelope;
import com.qlda.aiservice.dto.internal.DocumentAttachmentDto;
import com.qlda.aiservice.dto.internal.DocumentContentDto;
import com.qlda.aiservice.dto.internal.DocumentMetadataDto;
import com.qlda.aiservice.dto.internal.DocumentSearchDto;
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
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
public class DocumentInternalApiClient implements DocumentInternalApiService {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final String baseUrl;
    private final String token;
    private final String serviceName;

    public DocumentInternalApiClient(
        RestTemplate restTemplate,
        ObjectMapper objectMapper,
        @Value("${services.document.base-url:http://document-service:8082}") String baseUrl,
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
    public DocumentMetadataDto getDocument(Long id) {
        InternalApiEnvelope<?> envelope = exchange(baseUrl + "/internal/documents/" + id, HttpMethod.GET, null);
        return objectMapper.convertValue(envelope.data(), DocumentMetadataDto.class);
    }

    @Override
    public DocumentContentDto getDocumentContent(Long id) {
        InternalApiEnvelope<?> envelope = exchange(
            baseUrl + "/internal/documents/" + id + "/content", HttpMethod.GET, null
        );
        return objectMapper.convertValue(envelope.data(), DocumentContentDto.class);
    }

    @Override
    public List<DocumentAttachmentDto> getDocumentAttachments(Long id) {
        InternalApiEnvelope<?> envelope = exchange(
            baseUrl + "/internal/documents/" + id + "/attachments", HttpMethod.GET, null
        );
        List<Map<String, Object>> raw = objectMapper.convertValue(envelope.data(), new TypeReference<>() {});
        if (raw == null) return Collections.emptyList();
        return raw.stream()
            .map(item -> objectMapper.convertValue(item, DocumentAttachmentDto.class))
            .toList();
    }

    @Override
    public List<DocumentSearchDto> searchDocuments(String keyword, int limit) {
        String encoded = java.net.URLEncoder.encode(keyword == null ? "" : keyword, java.nio.charset.StandardCharsets.UTF_8);
        InternalApiEnvelope<?> envelope = exchange(
            baseUrl + "/internal/documents/search?keyword=" + encoded + "&limit=" + Math.max(1, Math.min(limit, 10)),
            HttpMethod.GET,
            null
        );
        List<Map<String, Object>> raw = objectMapper.convertValue(envelope.data(), new TypeReference<>() {});
        if (raw == null) return Collections.emptyList();
        return raw.stream()
            .map(item -> objectMapper.convertValue(item, DocumentSearchDto.class))
            .toList();
    }

    @Override
    public List<DocumentSearchDto> listDocumentsForAiIndex(int limit) {
        InternalApiEnvelope<?> envelope = exchange(
            baseUrl + "/internal/documents/indexable?limit=" + Math.max(1, Math.min(limit, 500)),
            HttpMethod.GET,
            null
        );
        List<Map<String, Object>> raw = objectMapper.convertValue(envelope.data(), new TypeReference<>() {});
        if (raw == null) return Collections.emptyList();
        return raw.stream()
            .map(item -> objectMapper.convertValue(item, DocumentSearchDto.class))
            .toList();
    }

    @Override
    public void updateOcrStatus(Long id, boolean daOcr) {
        exchange(
            baseUrl + "/internal/documents/" + id + "/ocr-status",
            HttpMethod.PATCH,
            Map.of("daOCR", daOcr)
        );
    }

    @Override
    public void updateOcrContent(Long id, String ocrText) {
        exchange(
            baseUrl + "/internal/documents/" + id + "/ocr-content",
            HttpMethod.PATCH,
            Map.of("ocrText", ocrText == null ? "" : ocrText)
        );
    }

    @Override
    public Set<Long> checkDocumentAccess(Long userId, List<Long> documentIds) {
        InternalApiEnvelope<?> envelope = exchange(
            baseUrl + "/internal/documents/access-check",
            HttpMethod.POST,
            Map.of("userId", userId, "documentIds", documentIds)
        );
        Map<String, Object> raw = objectMapper.convertValue(envelope.data(), new TypeReference<>() {});
        if (raw == null) return Set.of();
        List<?> allowedIds = (List<?>) raw.get("allowedDocumentIds");
        if (allowedIds == null) return Set.of();
        return allowedIds.stream()
            .filter(Number.class::isInstance)
            .map(Number.class::cast)
            .map(Number::longValue)
            .collect(java.util.stream.Collectors.toSet());
    }

    @Override
    public long getMyUploadedDocumentCount(Long userId) {
        InternalApiEnvelope<?> envelope = exchange(
            baseUrl + "/internal/documents/statistics/my-uploaded-count?userId=" + userId,
            HttpMethod.GET,
            null
        );
        return extractLongField(envelope, "count");
    }

    @Override
    public long getTotalDocumentCount() {
        InternalApiEnvelope<?> envelope = exchange(
            baseUrl + "/internal/documents/statistics/total-count",
            HttpMethod.GET,
            null
        );
        return extractLongField(envelope, "count");
    }

    @Override
    public long getTotalIncomingDocumentCount() {
        InternalApiEnvelope<?> envelope = exchange(
            baseUrl + "/internal/documents/statistics?groupBy=status",
            HttpMethod.GET,
            null
        );
        return extractLongField(envelope, "incomingDocuments");
    }

    @Override
    public long getDocumentThisMonthCount() {
        LocalDate today = LocalDate.now();
        String url = baseUrl
            + "/internal/documents/statistics?groupBy=status&fromDate="
            + today.withDayOfMonth(1)
            + "&toDate="
            + today;
        InternalApiEnvelope<?> envelope = exchange(url, HttpMethod.GET, null);
        return extractLongField(envelope, "totalDocuments");
    }

    private long extractLongField(InternalApiEnvelope<?> envelope, String fieldName) {
        Map<String, Object> raw = objectMapper.convertValue(envelope.data(), new TypeReference<>() {});
        Object value = raw == null ? null : raw.get(fieldName);
        return value instanceof Number number ? number.longValue() : 0L;
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
                    "Document service returned an invalid response: " + message
                );
            }
            return envelope;
        } catch (HttpClientErrorException.NotFound ex) {
            throw new AppException(ErrorCode.DOCUMENT_NOT_FOUND, HttpStatus.NOT_FOUND, "Document not found", ex);
        } catch (AppException ex) {
            throw ex;
        } catch (RestClientException ex) {
            log.error("Internal document-service request failed: method={} url={}", method, url, ex);
            throw new AppException(
                ErrorCode.INTERNAL_SERVER_ERROR,
                HttpStatus.BAD_GATEWAY,
                "Internal document-service request failed",
                ex
            );
        }
    }
}
