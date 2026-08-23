package com.qlda.notificationservice.client;

import com.qlda.notificationservice.client.dto.DocumentOverduePageResponse;
import com.qlda.notificationservice.client.dto.DocumentStatisticsClientResponse;
import com.qlda.notificationservice.client.dto.InternalApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

@Slf4j
@Component
public class DocumentServiceClient {

    private final WebClient webClient;

    public DocumentServiceClient(
        WebClient.Builder webClientBuilder,
        @Value("${services.document-service.base-url:http://document-service:8082}") String baseUrl,
        @Value("${internal.auth.service-name:notification-service}") String serviceName,
        @Value("${internal.auth.service-token:change-me-in-dev}") String serviceToken
    ) {
        this.webClient = webClientBuilder
            .baseUrl(baseUrl)
            .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + serviceToken)
            .defaultHeader("X-Service-Name", serviceName)
            .build();
    }

    public DocumentStatisticsClientResponse getDocumentStatistics(
        String fromDate,
        String toDate,
        Long donViId,
        String groupBy
    ) {
        try {
            InternalApiResponse<DocumentStatisticsClientResponse> response = webClient.get()
                .uri(uriBuilder -> {
                    var builder = uriBuilder.path("/internal/documents/statistics");
                    if (StringUtils.hasText(fromDate)) builder.queryParam("fromDate", fromDate);
                    if (StringUtils.hasText(toDate)) builder.queryParam("toDate", toDate);
                    if (donViId != null) builder.queryParam("donViId", donViId);
                    if (StringUtils.hasText(groupBy)) builder.queryParam("groupBy", groupBy);
                    return builder.build();
                })
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<InternalApiResponse<DocumentStatisticsClientResponse>>() {})
                .block();

            return requireData(response, "document statistics");
        } catch (WebClientResponseException ex) {
            log.error("Document service statistics failed: status={} body={}",
                ex.getStatusCode(), ex.getResponseBodyAsString(), ex);
            throw ex;
        }
    }

    public DocumentOverduePageResponse getOverdueDocuments(
        Long donViId,
        Long nguoiXuLyId,
        int page,
        int size
    ) {
        try {
            InternalApiResponse<DocumentOverduePageResponse> response = webClient.get()
                .uri(uriBuilder -> {
                    var builder = uriBuilder.path("/internal/documents/overdue")
                        .queryParam("page", page)
                        .queryParam("size", size);
                    if (donViId != null) builder.queryParam("donViId", donViId);
                    if (nguoiXuLyId != null) builder.queryParam("nguoiXuLyId", nguoiXuLyId);
                    return builder.build();
                })
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<InternalApiResponse<DocumentOverduePageResponse>>() {})
                .block();

            return requireData(response, "overdue documents");
        } catch (WebClientResponseException ex) {
            log.error("Document service overdue request failed: status={} body={}",
                ex.getStatusCode(), ex.getResponseBodyAsString(), ex);
            throw ex;
        }
    }

    private <T> T requireData(InternalApiResponse<T> response, String operation) {
        if (response == null) {
            throw new IllegalStateException("Document service returned an empty response for " + operation);
        }
        if (!response.success()) {
            throw new IllegalStateException(
                "Document service rejected " + operation + ": " + response.message()
            );
        }
        if (response.data() == null) {
            throw new IllegalStateException("Document service returned no data for " + operation);
        }
        return response.data();
    }
}
