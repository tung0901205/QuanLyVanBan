package com.qlda.aiservice.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qlda.aiservice.client.DocumentInternalApiService;
import com.qlda.aiservice.dto.request.ChatbotAskRequest;
import com.qlda.aiservice.dto.request.ClassifyRequest;
import com.qlda.aiservice.dto.request.IndexDocumentRequest;
import com.qlda.aiservice.dto.request.MetadataExtractRequest;
import com.qlda.aiservice.dto.request.SemanticSearchRequest;
import com.qlda.aiservice.dto.request.SuggestionHandlingRequest;
import com.qlda.aiservice.dto.request.SuggestionReplyRequest;
import com.qlda.aiservice.dto.request.SummarizeRequest;
import com.qlda.aiservice.entity.AiDocumentChunkEntity;
import com.qlda.aiservice.entity.AiProcessType;
import com.qlda.aiservice.entity.AiResultEntity;
import com.qlda.aiservice.exception.AppException;
import com.qlda.aiservice.exception.ErrorCode;
import com.qlda.aiservice.repository.AiDocumentChunkRepository;
import com.qlda.aiservice.repository.AiResultRepository;
import com.qlda.aiservice.security.CurrentUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class AiApplicationService {

    private static final int CHUNK_SIZE = 500;
    private static final int CHUNK_OVERLAP = 80;
    private static final int CHATBOT_TOP_K = 5;
    private static final double SEMANTIC_MIN_SCORE = 0.15;
    private static final List<String> ALLOWED_FILE_EXTENSIONS = List.of("pdf", "doc", "docx", "txt", "png", "jpg", "jpeg");
    private static final Set<String> ALLOWED_SUMMARY_TYPES = Set.of("SHORT", "DETAILED", "BULLET");
    private static final List<String> DEFAULT_CLASSIFICATION_CATEGORIES = List.of(
        "CONG_VAN", "QUYET_DINH", "THONG_BAO", "KE_HOACH", "BAO_CAO"
    );
    private static final List<String> DEFAULT_METADATA_FIELDS = List.of(
        "soKyHieu", "ngayVanBan", "nguoiKy", "doKhan"
    );

    private final AiResultRepository aiResultRepository;
    private final AiDocumentChunkRepository aiDocumentChunkRepository;
    private final AiModelService aiModelService;
    private final EmbeddingService embeddingService;
    private final OcrService ocrService;
    private final ObjectMapper objectMapper;
    private final DocumentInternalApiService documentInternalApiService;
    private final VectorSearchService vectorSearchService;
    private final DocumentTextExtractionService documentTextExtractionService;
    private final CurrentUserService currentUserService;

    public Map<String, Object> ocrFile(Long documentId, Long userId, MultipartFile file) {
        userId = currentUserService.resolveUserId(userId);
        validateFile(file);
        ensureDocumentAccess(documentId, userId);
        String text = ocrService.extractText(file);
        if (text == null || text.isBlank()) {
            throw new AppException(ErrorCode.OCR_FAILED, HttpStatus.UNPROCESSABLE_ENTITY, "Không nhận dạng được nội dung tệp");
        }
        documentInternalApiService.updateOcrContent(documentId, text);
        AiResultEntity saved = saveResult(
            documentId, userId, AiProcessType.OCR,
            file.getOriginalFilename(), text, 0.90, "tesseract-vie-eng+direct-text",
            "file=" + (file.getOriginalFilename() == null ? "upload" : file.getOriginalFilename())
        );
        try {
            aiDocumentChunkRepository.deleteByVanBanId(documentId);
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("type", "van_ban");
            metadata.put("source", "ocr");
            indexDocument(new IndexDocumentRequest(documentId, null, text, metadata));
        } catch (Exception ignored) { }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("resultId", saved.getId());
        result.put("documentId", documentId);
        result.put("ocrText", text);
        result.put("confidence", 0.90);
        result.put("modelUsed", "tesseract-vie-eng+direct-text");
        result.put("fileName", file.getOriginalFilename());
        return result;
    }

    public Map<String, Object> summarize(SummarizeRequest request) {
        validateSummarizeRequest(request);
        Long userId = currentUserService.resolveUserId(request.userId());
        ensureDocumentAccess(request.documentId(), userId);

        String sourceText = request.text();
        String source = "REQUEST";
        String attachmentName = null;
        if (sourceText == null || sourceText.isBlank()) {
            var extracted = documentTextExtractionService.extractBestText(request.documentId(), true);
            sourceText = extracted.text();
            source = extracted.source();
            attachmentName = extracted.attachmentName();
        }
        if (sourceText == null || sourceText.isBlank()) {
            throw new AppException(
                ErrorCode.AI_PROCESSING_FAILED,
                HttpStatus.UNPROCESSABLE_ENTITY,
                "Văn bản chưa có nội dung hoặc tệp đính kèm có thể đọc để tóm tắt"
            );
        }

        SummarizationOutput output = aiModelService.summarize(
            sourceText,
            request.summaryType().trim().toUpperCase(),
            normalizeLanguage(request.language())
        );
        String note = "summaryType=" + request.summaryType().trim().toUpperCase()
            + ",language=" + normalizeLanguage(request.language())
            + ",source=" + source
            + (attachmentName == null ? "" : ",attachment=" + attachmentName);
        AiResultEntity saved = saveResult(
            request.documentId(), userId, AiProcessType.SUMMARY,
            sourceText, output.summary(), output.confidence(), output.modelUsed(), note
        );

        // OCR/tệp vừa đọc cũng trở thành dữ liệu tìm kiếm ngữ nghĩa của chính văn bản.
        if (!"REQUEST".equals(source) && !"TITLE".equals(source)) {
            try {
                aiDocumentChunkRepository.deleteByVanBanId(request.documentId());
                Map<String, Object> metadata = new LinkedHashMap<>();
                metadata.put("type", "van_ban");
                metadata.put("source", source.toLowerCase());
                indexDocument(new IndexDocumentRequest(request.documentId(), null, sourceText, metadata));
            } catch (Exception ignored) {
                // Tóm tắt đã thành công; lỗi index không được làm hỏng kết quả người dùng.
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("resultId", saved.getId());
        result.put("documentId", request.documentId());
        result.put("summaryType", request.summaryType().trim().toUpperCase());
        result.put("summary", output.summary());
        result.put("modelUsed", output.modelUsed());
        result.put("confidence", output.confidence());
        result.put("source", source);
        if (attachmentName != null) result.put("attachmentName", attachmentName);
        return result;
    }

    public Map<String, Object> summarizeFile(Long documentId, Long userId, String summaryType, String language, MultipartFile file) {
        validateFile(file);
        String text = ocrService.extractText(file);
        Map<String, Object> data = summarize(new SummarizeRequest(documentId, userId, text, summaryType, language));
        return merge(data, Map.of("fileName", file.getOriginalFilename()));
    }

    public Map<String, Object> classify(ClassifyRequest request) {
        validateClassifyRequest(request);
        Long userId = currentUserService.resolveUserId(request.userId());
        ensureDocumentAccess(request.documentId(), userId);
        ClassificationOutput output = aiModelService.classify(request.text(), request.categories(), normalizeLanguage(request.language()));
        AiResultEntity saved = saveResult(
            request.documentId(), userId, AiProcessType.CLASSIFICATION,
            request.text(), toJson(Map.of("category", output.category(), "reason", output.reason())),
            output.confidence(), output.modelUsed(), output.reason()
        );
        return Map.of(
            "resultId", saved.getId(),
            "documentId", request.documentId(),
            "category", output.category(),
            "categoryName", output.categoryName(),
            "confidence", output.confidence(),
            "reason", output.reason(),
            "modelUsed", output.modelUsed()
        );
    }

    public Map<String, Object> classifyFile(Long documentId, Long userId, String language, MultipartFile file) {
        validateFile(file);
        String text = ocrService.extractText(file);
        Map<String, Object> data = classify(new ClassifyRequest(documentId, userId, text, DEFAULT_CLASSIFICATION_CATEGORIES, language));
        return merge(data, Map.of("fileName", file.getOriginalFilename()));
    }

    public Map<String, Object> extractMetadata(MetadataExtractRequest request) {
        validateMetadataRequest(request);
        Long userId = currentUserService.resolveUserId(request.userId());
        ensureDocumentAccess(request.documentId(), userId);
        MetadataOutput output = aiModelService.extractMetadata(request.text(), request.fields(), normalizeLanguage(request.language()));
        AiResultEntity saved = saveResult(
            request.documentId(), userId, AiProcessType.METADATA_EXTRACTION,
            request.text(), toJson(output.metadata()), output.confidence(), output.modelUsed(),
            "fields=" + String.join(",", request.fields())
        );
        return Map.of(
            "resultId", saved.getId(),
            "documentId", request.documentId(),
            "metadata", output.metadata(),
            "confidence", output.confidence(),
            "modelUsed", output.modelUsed()
        );
    }

    public Map<String, Object> extractMetadataFile(Long documentId, Long userId, String language, MultipartFile file) {
        validateFile(file);
        String text = ocrService.extractText(file);
        Map<String, Object> data = extractMetadata(new MetadataExtractRequest(documentId, userId, text, DEFAULT_METADATA_FIELDS, language));
        return merge(data, Map.of("fileName", file.getOriginalFilename()));
    }

    public Map<String, Object> semanticSearch(SemanticSearchRequest request) {
        Long userId = currentUserService.resolveUserId(request.userId());
        List<Double> keywordEmbedding = embeddingService.generateEmbedding(request.keyword());
        int page = request.page() == null ? 0 : Math.max(0, request.page());
        int size = request.size() == null ? 10 : Math.max(1, Math.min(50, request.size()));
        int fetchLimit = Math.max(50, (page + 1) * size * 6);

        Long filterDocumentId = extractDocumentId(request.filters());
        List<AiDocumentChunkEntity> chunks = vectorSearchService.findTopKDocumentChunksBySimilarity(
            keywordEmbedding, filterDocumentId, fetchLimit
        );

        // Một văn bản có thể có nhiều chunk. Màn hình tìm kiếm chỉ hiển thị
        // một dòng/văn bản, dùng chunk có độ tương đồng cao nhất.
        Map<Long, AiDocumentChunkEntity> bestByDocument = new LinkedHashMap<>();
        for (AiDocumentChunkEntity chunk : chunks) {
            if (chunk.getVanBanId() == null || chunk.getVanBanId() <= 0) continue;
            if (chunk.getSimilarityScore() != null && chunk.getSimilarityScore() < SEMANTIC_MIN_SCORE) continue;
            if (!matchesMetadataFilters(chunk, request.filters())) continue;
            bestByDocument.putIfAbsent(chunk.getVanBanId(), chunk);
        }

        List<Long> candidateIds = new ArrayList<>(bestByDocument.keySet());
        Set<Long> allowedIds = candidateIds.isEmpty()
            ? Set.of()
            : documentInternalApiService.checkDocumentAccess(userId, candidateIds);

        List<Map<String, Object>> ranked = candidateIds.stream()
            .filter(allowedIds::contains)
            .map(id -> toSearchItem(bestByDocument.get(id)))
            .toList();

        // Nếu DB vector chưa có dữ liệu cho một bộ dữ liệu cũ, vẫn trả về các
        // kết quả metadata chính xác thay vì hiển thị các chunk hướng dẫn.
        if (ranked.isEmpty()) {
            var fallback = documentInternalApiService.searchDocuments(request.keyword(), Math.min(20, size * 2));
            List<Long> fallbackIds = fallback.stream().map(item -> item.id()).filter(java.util.Objects::nonNull).toList();
            Set<Long> fallbackAllowed = fallbackIds.isEmpty() ? Set.of()
                : documentInternalApiService.checkDocumentAccess(userId, fallbackIds);
            ranked = fallback.stream()
                .filter(item -> item.id() != null && fallbackAllowed.contains(item.id()))
                .map(item -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("documentId", item.id());
                    row.put("soKyHieu", item.soKyHieu());
                    row.put("trichYeu", item.trichYeu());
                    row.put("tenLoaiVanBan", item.tenLoaiVanBan());
                    row.put("trangThai", item.trangThai());
                    row.put("score", 1.0);
                    row.put("matchedText", item.trichYeu());
                    return row;
                })
                .toList();
        }

        int from = Math.min(page * size, ranked.size());
        int to = Math.min(from + size, ranked.size());
        List<Map<String, Object>> content = ranked.subList(from, to);

        return Map.of(
            "content", content,
            "page", page,
            "size", size,
            "totalElements", ranked.size()
        );
    }

    @Transactional
    public Map<String, Object> indexDocument(IndexDocumentRequest request) {
        requireIndexPermission(request.documentId());
        ensureDocumentExists(request.documentId());
        aiDocumentChunkRepository.deleteByVanBanId(request.documentId());

        List<String> chunks = splitToChunks(request.text(), CHUNK_SIZE, CHUNK_OVERLAP);
        List<AiDocumentChunkEntity> entities = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) {
            String chunkText = chunks.get(i);
            List<Double> embedding = embeddingService.generateEmbedding(chunkText);
            AiDocumentChunkEntity entity = AiDocumentChunkEntity.builder()
                .vanBanId(request.documentId())
                .tepDinhKemId(request.attachmentId())
                .chunkIndex(i)
                .noiDung(chunkText)
                .embedding(vectorSearchService.toVectorString(embedding))
                .metadata(toJson(request.metadata()))
                .ngayTao(LocalDateTime.now())
                .build();
            entities.add(entity);
        }

        if (!entities.isEmpty()) {
            vectorSearchService.insertChunks(entities);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("documentId", request.documentId());
        result.put("attachmentId", request.attachmentId());
        result.put("totalChunks", entities.size());
        result.put("indexed", true);
        return result;
    }

    @Transactional
    public Map<String, Object> deleteDocumentIndex(Long documentId) {
        requireIndexPermission(documentId);
        ensureDocumentExists(documentId);
        aiDocumentChunkRepository.deleteByVanBanId(documentId);
        return Map.of("documentId", documentId, "deleted", true);
    }

    public Map<String, Object> suggestHandling(SuggestionHandlingRequest request) {
        Long userId = currentUserService.resolveUserId(request.userId());
        ensureDocumentAccess(request.documentId(), userId);
        SuggestionHandlingOutput output = aiModelService.suggestHandling(request.text(), request.context());
        AiResultEntity saved = saveResult(
            request.documentId(), userId, AiProcessType.SUGGESTION_HANDLING,
            request.text(), toJson(output.suggestions()), output.confidence(), output.modelUsed(), null
        );
        return Map.of(
            "resultId", saved.getId(),
            "documentId", request.documentId(),
            "suggestions", output.suggestions(),
            "confidence", output.confidence(),
            "modelUsed", output.modelUsed()
        );
    }

    public Map<String, Object> suggestReply(SuggestionReplyRequest request) {
        Long userId = currentUserService.resolveUserId(request.userId());
        ensureDocumentAccess(request.documentId(), userId);
        SuggestionReplyOutput output = aiModelService.suggestReply(request.text(), request.replyStyle(), request.language());
        AiResultEntity saved = saveResult(
            request.documentId(), userId, AiProcessType.SUGGESTION_REPLY,
            request.text(), output.suggestedReply(), output.confidence(), output.modelUsed(),
            "replyStyle=" + request.replyStyle()
        );
        return Map.of(
            "resultId", saved.getId(),
            "documentId", request.documentId(),
            "suggestedReply", output.suggestedReply(),
            "replyStyle", output.replyStyle(),
            "modelUsed", output.modelUsed(),
            "confidence", output.confidence()
        );
    }

    public Map<String, Object> askChatbot(ChatbotAskRequest request) {
        Long userId = currentUserService.resolveUserId(request.userId());
        Long contextDocumentId = extractDocumentId(request.context());
        if (contextDocumentId != null) {
            ensureDocumentAccess(contextDocumentId, userId);
        }

        List<Double> questionEmbedding = embeddingService.generateEmbedding(request.question());
        List<AiDocumentChunkEntity> topChunks = vectorSearchService.findTopKBySimilarity(
            questionEmbedding, contextDocumentId, CHATBOT_TOP_K
        );

        String context = topChunks.stream()
            .map(AiDocumentChunkEntity::getNoiDung)
            .reduce("", (a, b) -> a + "\n" + b)
            .trim();

        ChatbotOutput output = aiModelService.answerWithContext(request.question(), context);
        Long documentId = topChunks.isEmpty() ? null : topChunks.getFirst().getVanBanId();

        AiResultEntity saved = saveResult(
            documentId, userId, AiProcessType.CHATBOT,
            request.question(), output.answer(), output.confidence(), output.modelUsed(),
            request.context() == null ? null : toJson(request.context())
        );

        List<Map<String, Object>> sources = topChunks.stream().map(chunk -> {
            Map<String, Object> source = new LinkedHashMap<>();
            source.put("chunkId", chunk.getId());
            source.put("documentId", chunk.getVanBanId());
            source.put("chunkIndex", chunk.getChunkIndex());
            source.put("reference", "ai_document_chunk");
            source.put("matchedText", chunk.getNoiDung());
            return source;
        }).toList();

        return Map.of(
            "resultId", saved.getId(),
            "question", request.question(),
            "answer", output.answer(),
            "modelUsed", output.modelUsed(),
            "confidence", output.confidence(),
            "sources", sources
        );
    }

    public Map<String, Object> getResultsByDocument(Long documentId, String loaiXuLyAI, int page, int size) {
        Long userId = currentUserService.requireUserId();
        ensureDocumentAccess(documentId, userId);
        PageRequest pageable = PageRequest.of(Math.max(0, page), Math.max(1, size));
        Page<AiResultEntity> resultPage;
        if (loaiXuLyAI == null || loaiXuLyAI.isBlank()) {
            resultPage = aiResultRepository.findByVanBanID(documentId, pageable);
        } else {
            AiProcessType type = parseAiProcessType(loaiXuLyAI);
            resultPage = aiResultRepository.findByVanBanIDAndLoaiXuLyAI(documentId, type, pageable);
        }
        List<Map<String, Object>> content = resultPage.getContent().stream().map(this::mapResultSummary).toList();
        return Map.of(
            "content", content,
            "page", resultPage.getNumber(),
            "size", resultPage.getSize(),
            "totalElements", resultPage.getTotalElements()
        );
    }

    public Map<String, Object> getResultDetail(Long id) {
        AiResultEntity entity = aiResultRepository.findById(id)
            .orElseThrow(() -> new AppException(ErrorCode.AI_RESULT_NOT_FOUND, HttpStatus.NOT_FOUND, "AI result not found"));
        ensureCanReadResult(entity);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", entity.getId());
        result.put("documentId", entity.getVanBanID());
        result.put("userId", entity.getNguoiYeuCauID());
        result.put("loaiXuLyAI", entity.getLoaiXuLyAI());
        result.put("noiDungDauVao", entity.getNoiDungDauVao());
        result.put("ketQuaTraVe", entity.getKetQuaTraVe());
        result.put("confidence", entity.getDoTinCay());
        result.put("modelUsed", entity.getModelSuDung());
        result.put("processedAt", entity.getThoiGianXuLy());
        result.put("ghiChu", entity.getGhiChu());
        return result;
    }

    public Map<String, Object> deleteResult(Long id) {
        AiResultEntity entity = aiResultRepository.findById(id)
            .orElseThrow(() -> new AppException(ErrorCode.AI_RESULT_NOT_FOUND, HttpStatus.NOT_FOUND, "AI result not found"));
        Long userId = currentUserService.requireUserId();
        if (!currentUserService.isAdmin() && !java.util.Objects.equals(userId, entity.getNguoiYeuCauID())) {
            throw new AppException(ErrorCode.ACCESS_DENIED, HttpStatus.FORBIDDEN, "Only the result owner can delete it");
        }
        aiResultRepository.delete(entity);
        return Map.of("id", id, "deleted", true);
    }

    private AiResultEntity saveResult(
        Long documentId, Long userId, AiProcessType type,
        String input, String output, Double confidence, String modelUsed, String note
    ) {
        AiResultEntity entity = AiResultEntity.builder()
            .vanBanID(documentId)
            .nguoiYeuCauID(userId)
            .loaiXuLyAI(type)
            .noiDungDauVao(input)
            .ketQuaTraVe(output)
            .doTinCay(confidence)
            .modelSuDung(modelUsed)
            .thoiGianXuLy(LocalDateTime.now())
            .ghiChu(note)
            .build();
        return aiResultRepository.save(entity);
    }

    private Map<String, Object> toSearchItem(AiDocumentChunkEntity chunk) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("documentId", chunk.getVanBanId());
        row.put("chunkId", chunk.getId());
        row.put("chunkIndex", chunk.getChunkIndex());
        row.put("matchedText", chunk.getNoiDung());
        double score = chunk.getSimilarityScore() == null ? 0.0 : chunk.getSimilarityScore();
        row.put("score", Math.max(0.0, Math.min(1.0, score)));
        row.put("metadata", fromJsonMap(chunk.getMetadata()));
        try {
            var document = documentInternalApiService.getDocument(chunk.getVanBanId());
            row.put("soKyHieu", document.soKyHieu());
            row.put("trichYeu", document.trichYeu());
            row.put("tenLoaiVanBan", document.tenLoaiVanBan());
            row.put("trangThai", document.trangThai());
            row.put("documentType", document.documentType());
        } catch (Exception ignored) {
            Map<String, Object> metadata = fromJsonMap(chunk.getMetadata());
            row.put("soKyHieu", metadata.get("soKyHieu"));
            row.put("trichYeu", metadata.getOrDefault("trichYeu", chunk.getNoiDung()));
        }
        return row;
    }

    /**
     * Tách text theo ranh giới câu (sentence-aware) với overlap để giữ context.
     * Ưu tiên tách tại: dòng trống, xuống dòng, dấu câu kết thúc.
     */
    List<String> splitToChunks(String text, int chunkSize, int overlap) {
        List<String> chunks = new ArrayList<>();
        String safe = text == null ? "" : text.trim();
        if (safe.isEmpty()) return chunks;

        int start = 0;
        while (start < safe.length()) {
            int end = Math.min(start + chunkSize, safe.length());

            if (end < safe.length()) {
                int boundary = findSentenceBoundary(safe, start, end);
                if (boundary > start) end = boundary;
            }

            chunks.add(safe.substring(start, end).trim());
            int nextStart = end - overlap;
            start = nextStart > start ? nextStart : end;
        }
        return chunks;
    }

    private int findSentenceBoundary(String text, int start, int idealEnd) {
        String[] delimiters = {"\n\n", "\n", ". ", "! ", "? ", ".\n", "!\n", "?\n"};
        int best = -1;
        for (String delim : delimiters) {
            int idx = text.lastIndexOf(delim, idealEnd);
            if (idx > start && idx > best) {
                best = idx + delim.length();
            }
        }
        return best > start ? best : idealEnd;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> fromJsonMap(String json) {
        if (json == null || json.isBlank()) return Map.of();
        try {
            Map<String, Object> values = objectMapper.readValue(json, Map.class);
            return values == null ? Map.of() : values;
        } catch (Exception ex) {
            return Map.of();
        }
    }

    private String toJson(Object value) {
        if (value == null) return null;
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new AppException(ErrorCode.AI_PROCESSING_FAILED, HttpStatus.INTERNAL_SERVER_ERROR, "Serialize result failed");
        }
    }

    private Long extractDocumentId(Map<String, Object> filters) {
        if (filters == null) return null;
        Object value = filters.get("documentId");
        if (value instanceof Number number) return number.longValue();
        if (value instanceof String str && !str.isBlank()) {
            try { return Long.parseLong(str); }
            catch (NumberFormatException ex) {
                throw new AppException(ErrorCode.AI_PROCESSING_FAILED, HttpStatus.BAD_REQUEST, "Invalid documentId filter");
            }
        }
        return null;
    }

    private boolean matchesMetadataFilters(AiDocumentChunkEntity chunk, Map<String, Object> filters) {
        if (filters == null || filters.isEmpty()) return true;
        Map<String, Object> metadata = fromJsonMap(chunk.getMetadata());
        if (!matchesDateRange(filters, metadata)) return false;
        return filters.entrySet().stream()
            .filter(e -> !Set.of("documentId", "fromDate", "toDate").contains(e.getKey()))
            .allMatch(e -> {
                if (e.getValue() == null) return true;
                Object actual = metadata.get(e.getKey());
                return areEquivalent(actual, e.getValue());
            });
    }

    private boolean matchesDateRange(Map<String, Object> filters, Map<String, Object> metadata) {
        Object fromRaw = filters.get("fromDate");
        Object toRaw = filters.get("toDate");
        if (fromRaw == null && toRaw == null) return true;
        LocalDate valueDate = parseDate(metadata.get("ngayVanBan"));
        if (valueDate == null) return false;
        LocalDate from = parseDate(fromRaw);
        LocalDate to = parseDate(toRaw);
        if (from != null && valueDate.isBefore(from)) return false;
        return to == null || !valueDate.isAfter(to);
    }

    private LocalDate parseDate(Object raw) {
        if (raw == null) return null;
        String value = String.valueOf(raw).trim();
        if (value.isBlank()) return null;
        try { return LocalDate.parse(value); }
        catch (DateTimeParseException ex) { return null; }
    }

    private boolean areEquivalent(Object actual, Object expected) {
        if (actual == null) return false;
        if (actual instanceof Number an && expected instanceof Number en) {
            return Double.compare(an.doubleValue(), en.doubleValue()) == 0;
        }
        return String.valueOf(actual).trim().equalsIgnoreCase(String.valueOf(expected).trim());
    }

    private Map<String, Object> mapResultSummary(AiResultEntity entity) {
        return Map.of(
            "id", entity.getId(),
            "documentId", entity.getVanBanID(),
            "userId", entity.getNguoiYeuCauID(),
            "loaiXuLyAI", entity.getLoaiXuLyAI(),
            "ketQuaTraVe", entity.getKetQuaTraVe(),
            "confidence", entity.getDoTinCay(),
            "modelUsed", entity.getModelSuDung(),
            "processedAt", entity.getThoiGianXuLy()
        );
    }

    private Map<String, Object> merge(Map<String, Object> source, Map<String, Object> extra) {
        Map<String, Object> merged = new LinkedHashMap<>(source);
        merged.putAll(extra);
        return merged;
    }

    private String normalizeLanguage(String language) {
        return (language == null || language.isBlank()) ? "vi" : language.trim();
    }

    private void validateSummarizeRequest(SummarizeRequest request) {
        if (request == null || request.documentId() == null) {
            throw new AppException(ErrorCode.AI_PROCESSING_FAILED, HttpStatus.BAD_REQUEST, "Invalid summarize request");
        }
        if (isBlank(request.summaryType()) || !ALLOWED_SUMMARY_TYPES.contains(request.summaryType().trim().toUpperCase())) {
            throw new AppException(ErrorCode.AI_PROCESSING_FAILED, HttpStatus.BAD_REQUEST, "Invalid summaryType");
        }
    }

    private void validateClassifyRequest(ClassifyRequest request) {
        if (request == null || request.documentId() == null || request.userId() == null || isBlank(request.text())) {
            throw new AppException(ErrorCode.AI_PROCESSING_FAILED, HttpStatus.BAD_REQUEST, "Invalid classify request");
        }
        if (request.categories() == null || request.categories().isEmpty()) {
            throw new AppException(ErrorCode.AI_PROCESSING_FAILED, HttpStatus.BAD_REQUEST, "Categories cannot be empty");
        }
    }

    private void validateMetadataRequest(MetadataExtractRequest request) {
        if (request == null || request.documentId() == null || request.userId() == null || isBlank(request.text())) {
            throw new AppException(ErrorCode.AI_PROCESSING_FAILED, HttpStatus.BAD_REQUEST, "Invalid metadata request");
        }
        if (request.fields() == null || request.fields().isEmpty()) {
            throw new AppException(ErrorCode.AI_PROCESSING_FAILED, HttpStatus.BAD_REQUEST, "Fields cannot be empty");
        }
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new AppException(ErrorCode.INVALID_FILE_FORMAT, HttpStatus.BAD_REQUEST, "File is empty");
        }
        String fileName = file.getOriginalFilename();
        if (fileName == null || !fileName.contains(".")) {
            throw new AppException(ErrorCode.INVALID_FILE_FORMAT, HttpStatus.BAD_REQUEST, "Invalid file format");
        }
        String extension = fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase();
        if (!ALLOWED_FILE_EXTENSIONS.contains(extension)) {
            throw new AppException(ErrorCode.INVALID_FILE_FORMAT, HttpStatus.BAD_REQUEST, "Invalid file format");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private AiProcessType parseAiProcessType(String loaiXuLyAI) {
        try { return AiProcessType.valueOf(loaiXuLyAI.trim().toUpperCase()); }
        catch (IllegalArgumentException ex) {
            throw new AppException(ErrorCode.AI_PROCESSING_FAILED, HttpStatus.BAD_REQUEST, "Invalid loaiXuLyAI");
        }
    }

    private void ensureDocumentExists(Long documentId) {
        if (documentId == null) return;
        documentInternalApiService.getDocument(documentId);
    }

    private void ensureDocumentAccess(Long documentId, Long userId) {
        ensureDocumentExists(documentId);
        if (documentId == null || currentUserService.isInternalRequest()) return;
        Set<Long> allowed = documentInternalApiService.checkDocumentAccess(userId, List.of(documentId));
        if (!allowed.contains(documentId)) {
            throw new AppException(ErrorCode.ACCESS_DENIED, HttpStatus.FORBIDDEN, "You do not have access to this document");
        }
    }

    private void requireIndexPermission(Long documentId) {
        if (currentUserService.isInternalRequest()) return;
        Long userId = currentUserService.requireUserId();
        ensureDocumentAccess(documentId, userId);
        if (!currentUserService.isAdmin()) {
            throw new AppException(ErrorCode.ACCESS_DENIED, HttpStatus.FORBIDDEN, "Only administrators can rebuild the AI index");
        }
    }

    private void ensureCanReadResult(AiResultEntity entity) {
        Long userId = currentUserService.requireUserId();
        if (currentUserService.isAdmin() || java.util.Objects.equals(userId, entity.getNguoiYeuCauID())) return;
        if (entity.getVanBanID() != null) {
            ensureDocumentAccess(entity.getVanBanID(), userId);
            return;
        }
        throw new AppException(ErrorCode.ACCESS_DENIED, HttpStatus.FORBIDDEN, "You do not have access to this AI result");
    }
}
