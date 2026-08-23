package com.qlda.aiservice.service.chatbot;

import com.qlda.aiservice.service.GeminiService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class GeminiChatbotLlmService implements ChatbotLlmService {

    private static final String MODEL = "gemini-2.5-flash";
    private static final double DEFAULT_CONFIDENCE = 0.95;

    private final GeminiService geminiService;

    @Override
    public ChatbotLlmResponse generateAnswer(String systemPrompt, String userPrompt, String question) {
        if (!geminiService.isConfigured()) {
            return localFallback(userPrompt, question);
        }

        try {
            String answer = geminiService.chatWithSystem(systemPrompt, userPrompt);
            return new ChatbotLlmResponse(answer, DEFAULT_CONFIDENCE, MODEL);
        } catch (Exception exception) {
            log.warn("Gemini chatbot request failed; using local fallback: {}", exception.getMessage());
            return localFallback(userPrompt, question);
        }
    }

    private ChatbotLlmResponse localFallback(String userPrompt, String question) {
        String prompt = userPrompt == null ? "" : userPrompt;

        String metricLabel = extractLineValue(prompt, "Chỉ số:");
        String metricValue = extractLineValue(prompt, "Giá trị:");
        if (!metricLabel.isBlank() && !metricValue.isBlank()) {
            return new ChatbotLlmResponse(
                    "Hiện tại " + metricLabel.toLowerCase() + " là " + metricValue + ".",
                    0.90,
                    "local-fallback"
            );
        }

        List<String> steps = prompt.lines()
                .map(String::trim)
                .filter(line -> line.matches("(?i)^Bước\\s+\\d+.*"))
                .distinct()
                .limit(6)
                .toList();
        if (!steps.isEmpty()) {
            return new ChatbotLlmResponse(
                    String.join("\n", steps),
                    0.75,
                    "local-fallback"
            );
        }

        String documents = extractBlock(
                prompt,
                "Dữ liệu tìm được từ hệ thống:",
                "Hãy trả lời:"
        );
        if (!documents.isBlank()) {
            String shortened = documents.length() > 1200
                    ? documents.substring(0, 1200) + "..."
                    : documents;
            return new ChatbotLlmResponse(
                    "Tôi tìm thấy nội dung liên quan:\n" + shortened,
                    0.65,
                    "local-fallback"
            );
        }

        return new ChatbotLlmResponse(
                "Trợ lý AI đang chạy ở chế độ dự phòng. "
                        + "Bạn có thể hỏi về văn bản, phê duyệt, tải tài liệu, tìm kiếm hoặc số liệu hệ thống. "
                        + "Câu hỏi hiện tại: " + question,
                0.50,
                "local-fallback"
        );
    }

    private String extractLineValue(String text, String prefix) {
        return text.lines()
                .map(String::trim)
                .filter(line -> line.startsWith(prefix))
                .map(line -> line.substring(prefix.length()).trim())
                .findFirst()
                .orElse("");
    }

    private String extractBlock(String text, String startMarker, String endMarker) {
        int start = text.indexOf(startMarker);
        if (start < 0) return "";
        start += startMarker.length();
        int end = text.indexOf(endMarker, start);
        if (end < 0) end = text.length();
        return text.substring(start, end).trim();
    }
}
