package com.qlda.notificationservice.client;

import com.qlda.notificationservice.client.dto.InternalApiResponse;
import com.qlda.notificationservice.client.dto.SlaViolationClientItem;
import com.qlda.notificationservice.client.dto.WorkflowProgressClientResponse;
import com.qlda.notificationservice.client.dto.WorkflowStatisticsClientResponse;
import java.util.List;
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
public class WorkflowServiceClient {

    private final WebClient webClient;

    public WorkflowServiceClient(
        WebClient.Builder webClientBuilder,
        @Value("${services.workflow-service.base-url:http://workflow-service:8083}") String baseUrl,
        @Value("${internal.auth.service-name:notification-service}") String serviceName,
        @Value("${internal.auth.service-token:change-me-in-dev}") String serviceToken
    ) {
        this.webClient = webClientBuilder
            .baseUrl(baseUrl)
            .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + serviceToken)
            .defaultHeader("X-Service-Name", serviceName)
            .build();
    }

    public WorkflowStatisticsClientResponse getWorkflowStatistics(
        String fromDate,
        String toDate,
        Long donViId
    ) {
        try {
            InternalApiResponse<WorkflowStatisticsClientResponse> response = webClient.get()
                .uri(uriBuilder -> {
                    var builder = uriBuilder.path("/internal/workflows/statistics");
                    if (StringUtils.hasText(fromDate)) builder.queryParam("fromDate", fromDate);
                    if (StringUtils.hasText(toDate)) builder.queryParam("toDate", toDate);
                    if (donViId != null) builder.queryParam("donViId", donViId);
                    return builder.build();
                })
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<InternalApiResponse<WorkflowStatisticsClientResponse>>() {})
                .block();

            return requireData(response, "workflow statistics");
        } catch (WebClientResponseException ex) {
            log.error("Workflow service statistics failed: status={} body={}",
                ex.getStatusCode(), ex.getResponseBodyAsString(), ex);
            throw ex;
        }
    }

    public WorkflowProgressClientResponse getWorkflowProgress(
        String fromDate,
        String toDate,
        Long donViId,
        Long nguoiXuLyId
    ) {
        try {
            InternalApiResponse<WorkflowProgressClientResponse> response = webClient.get()
                .uri(uriBuilder -> {
                    var builder = uriBuilder.path("/internal/workflows/progress");
                    if (StringUtils.hasText(fromDate)) builder.queryParam("fromDate", fromDate);
                    if (StringUtils.hasText(toDate)) builder.queryParam("toDate", toDate);
                    if (donViId != null) builder.queryParam("donViId", donViId);
                    if (nguoiXuLyId != null) builder.queryParam("nguoiXuLyId", nguoiXuLyId);
                    return builder.build();
                })
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<InternalApiResponse<WorkflowProgressClientResponse>>() {})
                .block();

            return requireData(response, "workflow progress");
        } catch (WebClientResponseException ex) {
            log.error("Workflow service progress failed: status={} body={}",
                ex.getStatusCode(), ex.getResponseBodyAsString(), ex);
            throw ex;
        }
    }

    public List<SlaViolationClientItem> getSlaViolations(
        String fromDate,
        String toDate,
        Long donViId
    ) {
        try {
            InternalApiResponse<List<SlaViolationClientItem>> response = webClient.get()
                .uri(uriBuilder -> {
                    var builder = uriBuilder.path("/internal/workflows/sla/violations");
                    if (StringUtils.hasText(fromDate)) builder.queryParam("fromDate", fromDate);
                    if (StringUtils.hasText(toDate)) builder.queryParam("toDate", toDate);
                    if (donViId != null) builder.queryParam("donViId", donViId);
                    return builder.build();
                })
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<InternalApiResponse<List<SlaViolationClientItem>>>() {})
                .block();

            if (response == null || response.data() == null) return List.of();
            if (!response.success()) {
                throw new IllegalStateException("Workflow service rejected SLA request: " + response.message());
            }
            return response.data();
        } catch (WebClientResponseException ex) {
            log.error("Workflow service SLA request failed: status={} body={}",
                ex.getStatusCode(), ex.getResponseBodyAsString(), ex);
            throw ex;
        }
    }

    private <T> T requireData(InternalApiResponse<T> response, String operation) {
        if (response == null) {
            throw new IllegalStateException("Workflow service returned an empty response for " + operation);
        }
        if (!response.success()) {
            throw new IllegalStateException(
                "Workflow service rejected " + operation + ": " + response.message()
            );
        }
        if (response.data() == null) {
            throw new IllegalStateException("Workflow service returned no data for " + operation);
        }
        return response.data();
    }
}
