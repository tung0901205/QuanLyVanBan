package com.qlda.aiservice.service.chatbot;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qlda.aiservice.client.AuthInternalApiService;
import com.qlda.aiservice.client.DocumentInternalApiService;
import com.qlda.aiservice.client.WorkflowInternalApiService;
import com.qlda.aiservice.dto.request.ChatbotAskRequest;
import com.qlda.aiservice.entity.AiDocumentChunkEntity;
import com.qlda.aiservice.dto.internal.DocumentSearchDto;
import com.qlda.aiservice.entity.AiProcessType;
import com.qlda.aiservice.entity.AiResultEntity;
import com.qlda.aiservice.exception.AppException;
import com.qlda.aiservice.exception.ErrorCode;
import com.qlda.aiservice.repository.AiResultRepository;
import com.qlda.aiservice.security.CurrentUserService;
import com.qlda.aiservice.service.EmbeddingService;
import com.qlda.aiservice.service.VectorSearchService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class ChatbotService {

    private static final String NO_DATA_MESSAGE = "Không tìm thấy dữ liệu phù hợp.";
    private static final int DUE_SOON_DAYS = 3;
    private static final int MAX_HISTORY = 5;

    private final AiResultRepository aiResultRepository;
    private final EmbeddingService embeddingService;
    private final VectorSearchService vectorSearchService;
    private final ChatbotLlmService chatbotLlmService;
    private final DocumentInternalApiService documentInternalApiService;
    private final WorkflowInternalApiService workflowInternalApiService;
    private final AuthInternalApiService authInternalApiService;
    private final ChatbotIntentDetector chatbotIntentDetector;
    private final ChatbotPromptBuilder chatbotPromptBuilder;
    private final ChatbotResultMapper chatbotResultMapper;
    private final SystemKnowledgeService systemKnowledgeService;
    private final ObjectMapper objectMapper;
    private final int topK;
    private final CurrentUserService currentUserService;

    public ChatbotService(
        AiResultRepository aiResultRepository,
        EmbeddingService embeddingService,
        VectorSearchService vectorSearchService,
        ChatbotLlmService chatbotLlmService,
        DocumentInternalApiService documentInternalApiService,
        WorkflowInternalApiService workflowInternalApiService,
        AuthInternalApiService authInternalApiService,
        ChatbotIntentDetector chatbotIntentDetector,
        ChatbotPromptBuilder chatbotPromptBuilder,
        ChatbotResultMapper chatbotResultMapper,
        SystemKnowledgeService systemKnowledgeService,
        ObjectMapper objectMapper,
        CurrentUserService currentUserService,
        @Value("${chatbot.top-k:5}") int topK
    ) {
        this.aiResultRepository = aiResultRepository;
        this.embeddingService = embeddingService;
        this.vectorSearchService = vectorSearchService;
        this.chatbotLlmService = chatbotLlmService;
        this.documentInternalApiService = documentInternalApiService;
        this.workflowInternalApiService = workflowInternalApiService;
        this.authInternalApiService = authInternalApiService;
        this.chatbotIntentDetector = chatbotIntentDetector;
        this.chatbotPromptBuilder = chatbotPromptBuilder;
        this.chatbotResultMapper = chatbotResultMapper;
        this.systemKnowledgeService = systemKnowledgeService;
        this.objectMapper = objectMapper;
        this.currentUserService = currentUserService;
        this.topK = topK;
    }

    public Map<String, Object> ask(ChatbotAskRequest request) {
        Long userId = currentUserService.resolveUserId(request.userId());
        try {
            List<Map<String, String>> history = trimHistory(request.conversationHistory());
            IntentDetectionResult detectionResult = chatbotIntentDetector.detect(request.question());
            ChatbotExecution execution = switch (detectionResult.intent()) {
                case DOCUMENT_SEARCH -> processDocumentSearch(request, userId, history);
                case SYSTEM_STATISTIC -> processSystemStatistic(request, userId, detectionResult.metricCode(), history);
                case USER_GUIDE -> processUserGuide(request, history);
                case GENERAL_HELP -> processGeneralHelp(request, history);
            };

            AiResultEntity saved = saveResult(request, userId, detectionResult, execution);

            return chatbotResultMapper.toResponse(
                saved.getId(),
                detectionResult.intent(),
                detectionResult.metricCode(),
                request.question(),
                execution.answer(),
                execution.value(),
                execution.sources(),
                execution.modelUsed(),
                execution.confidence(),
                execution.responseType(),
                execution.structuredData()
            );
        } catch (AppException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new AppException(ErrorCode.CHATBOT_FAILED, HttpStatus.INTERNAL_SERVER_ERROR, "Chatbot processing failed", ex);
        }
    }

    private ChatbotExecution processDocumentSearch(ChatbotAskRequest request, Long userId,
                                                    List<Map<String, String>> history) {
        String keyword = extractSearchKeyword(request.question());
        if (!keyword.isBlank()) {
            try {
                List<DocumentSearchDto> matches = documentInternalApiService.searchDocuments(keyword, 10);
                if (!matches.isEmpty()) {
                    // Chỉ trả các văn bản người đang hỏi có quyền xem.
                    try {
                        Set<Long> allowed = documentInternalApiService.checkDocumentAccess(
                            userId, matches.stream().map(DocumentSearchDto::id).toList()
                        );
                        matches = matches.stream().filter(doc -> allowed.contains(doc.id())).limit(6).toList();
                    } catch (Exception ignored) {
                        // Nếu dịch vụ kiểm tra quyền tạm thời lỗi, không rò dữ liệu metadata:
                        // bỏ qua nhánh tìm kiếm trực tiếp và chuyển sang tìm kiếm ngữ nghĩa có kiểm tra quyền.
                        matches = List.of();
                    }
                }
                if (!matches.isEmpty()) {
                    List<Map<String, Object>> sources = new ArrayList<>();
                    StringBuilder answer = new StringBuilder("Tôi tìm thấy ")
                        .append(matches.size())
                        .append(" văn bản phù hợp:\n");
                    for (DocumentSearchDto doc : matches) {
                        Map<String, Object> source = new LinkedHashMap<>();
                        source.put("documentId", doc.id());
                        if (doc.trichYeu() != null) source.put("title", doc.trichYeu());
                        if (doc.soKyHieu() != null) source.put("soKyHieu", doc.soKyHieu());
                        source.put("reference", "DOCUMENT");
                        sources.add(source);
                        answer.append("- ")
                            .append(doc.soKyHieu() == null || doc.soKyHieu().isBlank() ? "VB-" + doc.id() : doc.soKyHieu())
                            .append(" — ")
                            .append(doc.trichYeu() == null ? "Không có trích yếu" : doc.trichYeu())
                            .append("\n");
                    }
                    Map<String, Object> structuredData = chatbotResultMapper.buildDocumentStructuredData(sources);
                    return new ChatbotExecution(
                        answer.toString().trim(), null, sources,
                        "system-search", 1.0, "DOCUMENT_LIST", structuredData
                    );
                }
            } catch (Exception ignored) {
                // Nếu tìm metadata lỗi, tiếp tục thử tìm kiếm ngữ nghĩa bên dưới.
            }
        }

        List<Double> queryEmbedding = embeddingService.generateEmbedding(request.question());
        Long contextDocumentId = extractDocumentId(request.context());
        List<AiDocumentChunkEntity> rankedChunks = vectorSearchService.findTopKBySimilarity(
            queryEmbedding, contextDocumentId, topK * 3
        );
        List<AiDocumentChunkEntity> accessibleChunks = filterByAccess(userId, contextDocumentId, rankedChunks);
        List<AiDocumentChunkEntity> topChunks = accessibleChunks.stream()
            .filter(chunk -> chunk.getVanBanId() != null && chunk.getVanBanId() > 0)
            .limit(topK)
            .toList();

        if (topChunks.isEmpty()) {
            return new ChatbotExecution(
                "Không tìm thấy văn bản phù hợp với từ khóa bạn cung cấp.",
                null, List.of(), "system-search", 1.0, "TEXT", null
            );
        }

        String retrievedDocuments = topChunks.stream()
            .map(AiDocumentChunkEntity::getNoiDung)
            .reduce("", (a, b) -> a + "\n" + b)
            .trim();
        String userPrompt = chatbotPromptBuilder.buildDocumentSearchPrompt(
            request.question(), retrievedDocuments, history);
        ChatbotLlmResponse llmResponse = chatbotLlmService.generateAnswer(
            chatbotPromptBuilder.systemPrompt(), userPrompt, request.question()
        );
        List<Map<String, Object>> sources = mapSources(topChunks);
        Map<String, Object> structuredData = chatbotResultMapper.buildDocumentStructuredData(sources);
        return new ChatbotExecution(
            llmResponse.answer(), null, sources,
            llmResponse.modelUsed(), llmResponse.confidence(),
            "DOCUMENT_LIST", structuredData
        );
    }

    private ChatbotExecution processUserGuide(ChatbotAskRequest request,
                                               List<Map<String, String>> history) {
        String answer = systemKnowledgeService.findAnswer(request.question())
            .orElse("Tôi nhận ra đây là câu hỏi về hệ thống, nhưng chưa có hướng dẫn đủ cụ thể. "
                + "Bạn có thể hỏi theo tên menu hoặc chức năng, ví dụ: Văn bản đến, Văn bản đi, Phê duyệt, "
                + "Ủy quyền, Template, Tìm kiếm, Thông báo, SLA, OCR hoặc Tóm tắt.");
        Map<String, Object> structuredData = systemKnowledgeService.buildGuideStructuredData(answer);
        String responseType = structuredData.isEmpty() ? "TEXT" : "GUIDE_STEPS";
        return new ChatbotExecution(
            answer, null, List.of(), "system-knowledge", 1.0, responseType,
            structuredData.isEmpty() ? null : structuredData
        );
    }

    private ChatbotExecution processGeneralHelp(ChatbotAskRequest request,
                                                 List<Map<String, String>> history) {
        String answer = "Tôi chỉ hỗ trợ các câu hỏi liên quan đến Hệ thống Quản lý Văn bản và Điều hành tác nghiệp, "
            + "ví dụ: chức năng các menu, luồng xử lý/phê duyệt, ủy quyền, template, tìm kiếm văn bản theo mã hoặc tên, "
            + "thống kê, SLA, OCR và tóm tắt văn bản. Tôi không trả lời các câu hỏi ngoài phạm vi hệ thống.";
        return new ChatbotExecution(answer, null, List.of(),
            "scope-guard", 1.0, "TEXT", null);
    }

    private ChatbotExecution processSystemStatistic(ChatbotAskRequest request, Long userId,
                                                     ChatbotMetricCode metricCode,
                                                     List<Map<String, String>> history) {
        if (metricCode == null) {
            return new ChatbotExecution(NO_DATA_MESSAGE, null, List.of(), "system", 1.0, "TEXT", null);
        }
        long value = switch (metricCode) {
            case MY_UPLOADED_DOCUMENT_COUNT -> documentInternalApiService.getMyUploadedDocumentCount(userId);
            case MY_PENDING_DOCUMENT_COUNT -> workflowInternalApiService.getMyPendingDocumentCount(userId);
            case MY_COMPLETED_DOCUMENT_COUNT -> workflowInternalApiService.getMyCompletedDocumentCount(userId);
            case MY_DUE_SOON_DOCUMENT_COUNT -> workflowInternalApiService.getMyDueSoonDocumentCount(userId, DUE_SOON_DAYS);
            case MY_OVERDUE_DOCUMENT_COUNT -> workflowInternalApiService.getMyOverdueDocumentCount(userId);
            case TOTAL_DOCUMENT_COUNT -> documentInternalApiService.getTotalDocumentCount();
            case TOTAL_USER_COUNT -> getTotalUserCountWithPermissionCheck(userId);
            case TOTAL_INCOMING_DOCUMENT_COUNT -> documentInternalApiService.getTotalIncomingDocumentCount();
            case SLA_VIOLATION_COUNT -> workflowInternalApiService.getSlaViolationCount();
            case DOCUMENT_THIS_MONTH_COUNT -> documentInternalApiService.getDocumentThisMonthCount();
        };
        String label = chatbotPromptBuilder.getMetricLabel(metricCode);
        String answer = "Hiện tại " + label.toLowerCase() + " là " + value + ".";
        Map<String, Object> structuredData = chatbotResultMapper.buildStatCardStructuredData(metricCode, value, label);
        return new ChatbotExecution(answer, value, List.of(),
            "system-statistic", 1.0, "STAT_CARD", structuredData);
    }

    private long getTotalUserCountWithPermissionCheck(Long userId) {
        if (!authInternalApiService.hasSystemStatisticPermission(userId)) {
            throw new AppException(ErrorCode.CHATBOT_FAILED, HttpStatus.FORBIDDEN,
                "User has no permission for TOTAL_USER_COUNT");
        }
        return authInternalApiService.getTotalUserCount(userId);
    }

    private List<AiDocumentChunkEntity> filterByAccess(Long userId, Long contextDocumentId,
                                                        List<AiDocumentChunkEntity> chunks) {
        LinkedHashMap<Long, Boolean> docIds = new LinkedHashMap<>();
        for (AiDocumentChunkEntity chunk : chunks) {
            if (chunk.getVanBanId() != null && chunk.getVanBanId() > 0) {
                docIds.put(chunk.getVanBanId(), true);
            }
        }
        if (contextDocumentId != null) docIds.put(contextDocumentId, true);
        if (docIds.isEmpty()) return chunks;

        try {
            Set<Long> allowed = documentInternalApiService.checkDocumentAccess(
                userId, new ArrayList<>(docIds.keySet())
            );
            return chunks.stream()
                .filter(c -> c.getVanBanId() == null || c.getVanBanId() <= 0 || allowed.contains(c.getVanBanId()))
                .toList();
        } catch (Exception ex) {
            // Fail closed: guide chunks are public system knowledge, document chunks are not.
            return chunks.stream()
                .filter(c -> c.getVanBanId() == null || c.getVanBanId() <= 0)
                .toList();
        }
    }

    private List<Map<String, Object>> mapSources(List<AiDocumentChunkEntity> chunks) {
        List<Map<String, Object>> sources = new ArrayList<>();
        for (AiDocumentChunkEntity chunk : chunks) {
            Map<String, Object> source = new LinkedHashMap<>();
            source.put("documentId", chunk.getVanBanId());
            source.put("chunkId", chunk.getId());
            source.put("matchedText", chunk.getNoiDung());
            Map<String, Object> metadata = fromJsonMap(chunk.getMetadata());
            Object title = metadata.get("title") != null ? metadata.get("title") : metadata.get("trichYeu");
            if (title != null) source.put("title", title);
            Object soKyHieu = metadata.get("soKyHieu");
            if (soKyHieu != null) source.put("soKyHieu", soKyHieu);
            Object type = metadata.get("type");
            source.put("reference", type == null ? "DOCUMENT" : String.valueOf(type));
            sources.add(source);
        }
        return sources;
    }

    private String extractSearchKeyword(String question) {
        if (question == null) return "";
        String q = question.trim();
        String cleaned = q
            .replaceAll("(?iu)^(hãy\\s+)?(tìm kiếm|tìm|tra cứu|tìm giúp|tìm cho tôi|cho tôi xem)\\s+", "")
            .replaceAll("(?iu)^(văn bản|tài liệu)\\s+(có\\s+)?(mã|số|tên|về)?\\s*", "")
            .replaceAll("(?iu)\\b(văn bản|tài liệu)\\b", " ")
            .replaceAll("(?iu)\\b(có tên|tên là|mã là|số là)\\b", " ")
            .replaceAll("[?]+$", "")
            .replaceAll("\\s+", " ")
            .trim();
        return cleaned.length() > 2 ? cleaned : q;
    }

    private List<Map<String, String>> trimHistory(List<Map<String, String>> history) {
        if (history == null || history.isEmpty()) return List.of();
        int size = history.size();
        if (size <= MAX_HISTORY) return history;
        return history.subList(size - MAX_HISTORY, size);
    }

    private AiResultEntity saveResult(
        ChatbotAskRequest request,
        Long userId,
        IntentDetectionResult detectionResult,
        ChatbotExecution execution
    ) {
        Map<String, Object> noteData = new LinkedHashMap<>();
        noteData.put("intent", detectionResult.intent().name());
        if (detectionResult.metricCode() != null) noteData.put("metricCode", detectionResult.metricCode().name());
        if (!execution.sources().isEmpty()) noteData.put("sources", execution.sources());
        if (request.currentModule() != null) noteData.put("module", request.currentModule());

        AiResultEntity entity = AiResultEntity.builder()
            .vanBanID(extractDocumentId(request.context()))
            .nguoiYeuCauID(userId)
            .loaiXuLyAI(AiProcessType.CHATBOT)
            .noiDungDauVao(request.question())
            .ketQuaTraVe(execution.answer())
            .doTinCay(execution.confidence())
            .modelSuDung(execution.modelUsed())
            .thoiGianXuLy(LocalDateTime.now())
            .ghiChu(toJson(noteData))
            .build();
        return aiResultRepository.save(entity);
    }

    private String toJson(Object input) {
        try { return objectMapper.writeValueAsString(input); }
        catch (JsonProcessingException ex) { return "{}"; }
    }

    private Long extractDocumentId(Map<String, Object> context) {
        if (context == null) return null;
        Object documentId = context.get("documentId");
        if (documentId instanceof Number n) return n.longValue();
        if (documentId instanceof String s && !s.isBlank()) {
            try { return Long.parseLong(s); } catch (NumberFormatException ignored) {}
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> fromJsonMap(String json) {
        if (json == null || json.isBlank()) return Map.of();
        try { return objectMapper.readValue(json, Map.class); }
        catch (Exception ex) { return Map.of(); }
    }

    private record ChatbotExecution(
        String answer,
        Long value,
        List<Map<String, Object>> sources,
        String modelUsed,
        double confidence,
        String responseType,
        Map<String, Object> structuredData
    ) {}
}
