package com.qlda.aiservice.service;

import com.qlda.aiservice.config.GeminiConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Service
@Primary
@RequiredArgsConstructor
public class GeminiEmbeddingService implements EmbeddingService {

    private static final int EMBEDDING_DIMENSION = 768;
    private static final int MAX_INPUT_CHARS = 8000;

    private final RestTemplate restTemplate;
    private final GeminiConfig geminiConfig;

    @Value("${gemini.api.embedding-model:gemini-embedding-2}")
    private String embeddingModel;

    @Override
    @SuppressWarnings("unchecked")
    public List<Double> generateEmbedding(String text) {
        if (text == null || text.isBlank()) {
            return zeroVector();
        }

        if (!geminiConfig.isConfigured()) {
            log.warn("GEMINI_API_KEY is empty; using deterministic local embedding fallback");
            return localEmbedding(text);
        }

        String truncated = text.length() > MAX_INPUT_CHARS
                ? text.substring(0, MAX_INPUT_CHARS)
                : text;

        String url = geminiConfig.buildEmbeddingUrl(embeddingModel);
        Map<String, Object> requestBody = Map.of(
                "content", Map.of(
                        "parts", List.of(Map.of("text", truncated))
                ),
                "output_dimensionality", EMBEDDING_DIMENSION
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        headers.set("x-goog-api-key", geminiConfig.getApiKey());

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(
                    url,
                    new HttpEntity<>(requestBody, headers),
                    Map.class
            );

            List<?> values = extractEmbeddingValues(response.getBody());
            if (values == null || values.isEmpty()) {
                log.warn("Gemini embedding response is empty; using local fallback");
                return localEmbedding(text);
            }

            List<Double> result = values.stream()
                    .map(value -> ((Number) value).doubleValue())
                    .toList();

            if (result.size() != EMBEDDING_DIMENSION) {
                log.warn("Unexpected embedding dimension {}; expected {}. Using local fallback",
                        result.size(), EMBEDDING_DIMENSION);
                return localEmbedding(text);
            }

            return result;
        } catch (Exception exception) {
            log.warn("Gemini embedding failed; using local fallback: {}", exception.getMessage());
            return localEmbedding(text);
        }
    }

    private List<?> extractEmbeddingValues(Map<?, ?> body) {
        if (body == null) {
            return null;
        }

        // REST embedContent may return either embedding or embeddings depending on API version.
        Object embedding = body.get("embedding");
        if (embedding instanceof Map<?, ?> embeddingMap) {
            Object values = embeddingMap.get("values");
            if (values instanceof List<?> list) {
                return list;
            }
        }

        Object embeddings = body.get("embeddings");
        if (embeddings instanceof List<?> list && !list.isEmpty() && list.getFirst() instanceof Map<?, ?> first) {
            Object values = first.get("values");
            if (values instanceof List<?> valueList) {
                return valueList;
            }
        }

        return null;
    }

    private List<Double> localEmbedding(String text) {
        double[] values = new double[EMBEDDING_DIMENSION];
        String normalized = text.toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}]+", " ")
                .trim();

        if (normalized.isEmpty()) {
            return zeroVector();
        }

        for (String token : normalized.split("\\s+")) {
            int index1 = Math.floorMod(token.hashCode(), EMBEDDING_DIMENSION);
            int index2 = Math.floorMod((token + "#").hashCode(), EMBEDDING_DIMENSION);
            values[index1] += 1.0;
            values[index2] += 0.5;
        }

        double norm = 0.0;
        for (double value : values) {
            norm += value * value;
        }
        norm = Math.sqrt(norm);

        List<Double> result = new ArrayList<>(EMBEDDING_DIMENSION);
        for (double value : values) {
            result.add(norm == 0.0 ? 0.0 : value / norm);
        }
        return result;
    }

    private List<Double> zeroVector() {
        List<Double> result = new ArrayList<>(EMBEDDING_DIMENSION);
        for (int i = 0; i < EMBEDDING_DIMENSION; i++) {
            result.add(0.0);
        }
        return result;
    }
}
