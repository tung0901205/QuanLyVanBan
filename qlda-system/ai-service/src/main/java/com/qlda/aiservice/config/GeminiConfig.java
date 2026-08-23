package com.qlda.aiservice.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.restclient.RestTemplateBuilder;
import java.time.Duration;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

@Configuration
public class GeminiConfig {

    @Value("${gemini.api.key:}")
    private String apiKey;

    @Value("${gemini.api.model:gemini-2.5-flash}")
    private String model;

    @Value("${gemini.api.url:https://generativelanguage.googleapis.com/v1beta/models}")
    private String baseUrl;

    @Value("${gemini.api.connect-timeout-ms:5000}")
    private long connectTimeoutMs;

    @Value("${gemini.api.read-timeout-ms:30000}")
    private long readTimeoutMs;

    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        return builder
                .connectTimeout(Duration.ofMillis(connectTimeoutMs))
                .readTimeout(Duration.ofMillis(readTimeoutMs))
                .build();
    }

    public String getApiKey() {
        return apiKey == null ? "" : apiKey.trim();
    }

    public String getModel() {
        return model;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public boolean isConfigured() {
        return StringUtils.hasText(getApiKey());
    }

    public String buildApiUrl() {
        return baseUrl + "/" + model + ":generateContent";
    }

    public String buildEmbeddingUrl(String embeddingModel) {
        return baseUrl + "/" + embeddingModel + ":embedContent";
    }
}
