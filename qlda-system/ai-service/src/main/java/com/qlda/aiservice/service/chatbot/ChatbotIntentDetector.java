package com.qlda.aiservice.service.chatbot;

import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;

@Component
public class ChatbotIntentDetector {

    private final SystemKnowledgeService systemKnowledgeService;

    public ChatbotIntentDetector(SystemKnowledgeService systemKnowledgeService) {
        this.systemKnowledgeService = systemKnowledgeService;
    }

    private static final List<String> COUNT_CUES = List.of(
        "bao nhieu", "co may", "so luong", "tong so", "thong ke", "dem", "bao nhiêu", "có mấy", "số lượng", "tổng số"
    );

    private static final List<String> GUIDE_CUES = List.of(
        "cach", "lam sao", "lam the nao", "huong dan", "la gi", "dung de lam gi", "chuc nang", "y nghia",
        "thuc hien", "quy trinh", "luong hoat dong", "cách", "làm sao", "làm thế nào", "hướng dẫn", "là gì",
        "dùng để làm gì", "chức năng", "ý nghĩa", "thực hiện", "luồng hoạt động"
    );

    private static final List<String> SEARCH_CUES = List.of(
        "tim", "tim kiem", "tra cuu", "tim cho toi", "tim giup", "cho toi xem", "van ban ma", "van ban so",
        "tìm", "tìm kiếm", "tra cứu", "tìm cho tôi", "tìm giúp", "cho tôi xem", "văn bản mã", "văn bản số"
    );

    public IntentDetectionResult detect(String question) {
        String normalized = normalize(question);
        if (normalized.isBlank()) {
            return new IntentDetectionResult(ChatbotIntent.GENERAL_HELP, null);
        }

        // Hướng dẫn/giải thích phải ưu tiên trước thống kê để câu "cách tải lên văn bản"
        // không bị hiểu nhầm thành "đếm số văn bản đã tải lên".
        if (matchesAny(normalized, GUIDE_CUES) && systemKnowledgeService.isSystemRelated(question)) {
            return new IntentDetectionResult(ChatbotIntent.USER_GUIDE, null);
        }

        if (matchesAny(normalized, COUNT_CUES)) {
            ChatbotMetricCode metric = detectMetric(normalized);
            if (metric != null) {
                return new IntentDetectionResult(ChatbotIntent.SYSTEM_STATISTIC, metric);
            }
        }

        if (matchesAny(normalized, SEARCH_CUES) && containsDocumentTerm(normalized)) {
            return new IntentDetectionResult(ChatbotIntent.DOCUMENT_SEARCH, null);
        }

        // Một số câu thống kê rất tự nhiên không chứa "bao nhiêu" nhưng có ý đếm rõ ràng.
        if ((normalized.startsWith("toi co ") || normalized.startsWith("hien co ")) && containsDocumentTerm(normalized)) {
            ChatbotMetricCode metric = detectMetric(normalized);
            if (metric != null) {
                return new IntentDetectionResult(ChatbotIntent.SYSTEM_STATISTIC, metric);
            }
        }

        // Các câu hỏi/thuật ngữ thuộc menu và nghiệp vụ hệ thống được trả lời bằng kho kiến thức nội bộ.
        if (systemKnowledgeService.isSystemRelated(question)) {
            if (matchesAny(normalized, SEARCH_CUES)) {
                return new IntentDetectionResult(ChatbotIntent.DOCUMENT_SEARCH, null);
            }
            return new IntentDetectionResult(ChatbotIntent.USER_GUIDE, null);
        }

        // Ngoài phạm vi hệ thống: không gửi sang LLM để tránh trả lời lan man.
        return new IntentDetectionResult(ChatbotIntent.GENERAL_HELP, null);
    }

    private ChatbotMetricCode detectMetric(String text) {
        if (containsAny(text, "cho xu ly", "dang cho", "chua xu ly", "can xu ly")) {
            return ChatbotMetricCode.MY_PENDING_DOCUMENT_COUNT;
        }
        if (containsAny(text, "qua han", "tre han", "vi pham han")) {
            return ChatbotMetricCode.MY_OVERDUE_DOCUMENT_COUNT;
        }
        if (containsAny(text, "sap het han", "sap den han", "sap qua han")) {
            return ChatbotMetricCode.MY_DUE_SOON_DOCUMENT_COUNT;
        }
        if (containsAny(text, "da xu ly", "hoan thanh", "xu ly xong")) {
            return ChatbotMetricCode.MY_COMPLETED_DOCUMENT_COUNT;
        }
        if (containsAny(text, "toi da tai", "toi tai", "da upload", "upload cua toi")) {
            return ChatbotMetricCode.MY_UPLOADED_DOCUMENT_COUNT;
        }
        if (containsAny(text, "nguoi dung", "tai khoan")) {
            return ChatbotMetricCode.TOTAL_USER_COUNT;
        }
        if (containsAny(text, "van ban den")) {
            return ChatbotMetricCode.TOTAL_INCOMING_DOCUMENT_COUNT;
        }
        if (containsAny(text, "vi pham sla")) {
            return ChatbotMetricCode.SLA_VIOLATION_COUNT;
        }
        if (containsAny(text, "thang nay", "trong thang", "thang hien tai")) {
            return ChatbotMetricCode.DOCUMENT_THIS_MONTH_COUNT;
        }
        if (containsAny(text, "van ban", "tai lieu")) {
            return ChatbotMetricCode.TOTAL_DOCUMENT_COUNT;
        }
        return null;
    }

    private boolean containsDocumentTerm(String text) {
        return containsAny(text, "van ban", "cong van", "quyet dinh", "bien ban", "tai lieu", "so ky hieu", "ma ");
    }

    private boolean containsAny(String text, String... values) {
        for (String value : values) {
            if (text.contains(normalize(value))) return true;
        }
        return false;
    }

    private boolean matchesAny(String text, List<String> keywords) {
        for (String kw : keywords) {
            if (text.contains(normalize(kw))) return true;
        }
        return false;
    }

    private String normalize(String input) {
        if (input == null) return "";
        String lowered = input.toLowerCase(Locale.ROOT).trim();
        String nfd = Normalizer.normalize(lowered, Normalizer.Form.NFD);
        return nfd
            .replaceAll("\\p{M}+", "")
            .replace('đ', 'd')
            .replaceAll("[^a-z0-9\\s/._-]", " ")
            .replaceAll("\\s+", " ")
            .trim();
    }
}
