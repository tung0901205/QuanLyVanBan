package com.qlda.aiservice.controller;

import com.qlda.aiservice.client.DocumentInternalApiService;
import com.qlda.aiservice.dto.internal.DocumentContentDto;
import com.qlda.aiservice.dto.request.IndexDocumentRequest;
import com.qlda.aiservice.service.AiApplicationService;
import com.qlda.aiservice.service.AiModelService;
import com.qlda.aiservice.service.ClassificationOutput;
import com.qlda.aiservice.service.MetadataOutput;
import com.qlda.aiservice.service.OcrService;
import com.qlda.aiservice.service.SuggestionHandlingOutput;
import com.qlda.aiservice.service.SummarizationOutput;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/internal/ai")
public class InternalAiController {

    private final OcrService ocrService;
    private final AiModelService aiModelService;
    private final AiApplicationService aiApplicationService;
    private final DocumentInternalApiService documentInternalApiService;
    private final Path uploadRoot;

    public InternalAiController(
        OcrService ocrService,
        AiModelService aiModelService,
        AiApplicationService aiApplicationService,
        DocumentInternalApiService documentInternalApiService,
        @Value("${ai.upload-root:/app/uploads}") String uploadRoot
    ) {
        this.ocrService = ocrService;
        this.aiModelService = aiModelService;
        this.aiApplicationService = aiApplicationService;
        this.documentInternalApiService = documentInternalApiService;
        this.uploadRoot = Paths.get(uploadRoot).toAbsolutePath().normalize();
    }

    @PostMapping("/ocr")
    public OcrResponse ocr(@RequestBody OcrRequest request) {
        Path path = resolveUploadPath(request.fileUrl());
        String text = ocrService.extractText(path);
        return new OcrResponse(request.documentId(), text, 0.90, "tesseract-vie-eng");
    }

    @PostMapping("/summarize")
    public SummarizeResponse summarize(@RequestBody SummarizeRequest request) {
        String content = resolveContent(request.documentId(), request.content());
        SummarizationOutput output = aiModelService.summarize(content, normalizeSummaryType(request.summaryType()), "vi");
        return new SummarizeResponse(request.documentId(), normalizeSummaryType(request.summaryType()), output.summary(), output.confidence(), output.modelUsed());
    }

    @PostMapping("/classify")
    public ClassifyResponse classify(@RequestBody ClassifyRequest request) {
        String content = resolveContent(request.documentId(), request.content());
        ClassificationOutput output = aiModelService.classify(
            content,
            List.of("CONG_VAN", "QUYET_DINH", "THONG_BAO", "KE_HOACH", "BAO_CAO", "BIEN_BAN", "DE_XUAT"),
            "vi"
        );
        return new ClassifyResponse(request.documentId(), output.category(), output.categoryName(), output.confidence(), output.reason());
    }

    @PostMapping("/metadata/extract")
    public ExtractMetadataResponse extractMetadata(@RequestBody ExtractMetadataRequest request) {
        String content = resolveContent(request.documentId(), request.content());
        MetadataOutput output = aiModelService.extractMetadata(content, List.of("soKyHieu", "ngayVanBan", "nguoiKy", "doKhan"), "vi");
        Map<String, String> metadata = new LinkedHashMap<>();
        output.metadata().forEach((key, value) -> metadata.put(key, value == null ? null : String.valueOf(value)));
        return new ExtractMetadataResponse(request.documentId(), metadata, output.confidence(), output.modelUsed());
    }

    @PostMapping("/suggestions")
    public SuggestionResponse suggestions(@RequestBody SuggestionRequest request) {
        String content = resolveContent(request.documentId(), request.content());
        SuggestionHandlingOutput output = aiModelService.suggestHandling(content, Map.of());
        List<SuggestionItem> items = output.suggestions().stream()
            .map(item -> new SuggestionItem(item.action(), item.description(), item.priority()))
            .toList();
        return new SuggestionResponse(request.documentId(), items, output.confidence());
    }

    @PostMapping("/index-document/{documentId}")
    public IndexDocumentResponse indexDocument(
        @PathVariable Long documentId,
        @RequestBody(required = false) IndexTrigger request
    ) {
        DocumentContentDto content = documentInternalApiService.getDocumentContent(documentId);
        String text = content.ocrText() != null && !content.ocrText().isBlank()
            ? content.ocrText()
            : content.noiDung();
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("trichYeu", content.trichYeu());
        metadata.put("source", request == null || request.triggeredBy() == null ? "document-service" : request.triggeredBy());
        Map<String, Object> result = aiApplicationService.indexDocument(
            new IndexDocumentRequest(documentId, null, text == null || text.isBlank() ? content.trichYeu() : text, metadata)
        );
        Integer totalChunks = result.get("totalChunks") instanceof Number n ? n.intValue() : 0;
        return new IndexDocumentResponse(true, "Index document successfully", new IndexDocumentData(documentId, true, totalChunks));
    }

    private String resolveContent(Long documentId, String provided) {
        if (provided != null && !provided.isBlank()) return provided;
        DocumentContentDto content = documentInternalApiService.getDocumentContent(documentId);
        if (content.ocrText() != null && !content.ocrText().isBlank()) return content.ocrText();
        if (content.noiDung() != null && !content.noiDung().isBlank()) return content.noiDung();
        return content.trichYeu() == null ? "" : content.trichYeu();
    }

    private String normalizeSummaryType(String value) {
        if (value == null || value.isBlank()) return "SHORT";
        String upper = value.trim().toUpperCase();
        return List.of("SHORT", "DETAILED", "BULLET").contains(upper) ? upper : "SHORT";
    }

    private Path resolveUploadPath(String fileUrl) {
        if (fileUrl == null || fileUrl.isBlank()) {
            throw new IllegalArgumentException("fileUrl is required");
        }
        String normalized = fileUrl.replace('\\', '/').trim();
        String filename = normalized.startsWith("/uploads/")
            ? normalized.substring("/uploads/".length())
            : Paths.get(normalized).getFileName().toString();
        Path resolved = uploadRoot.resolve(filename).normalize();
        if (!resolved.startsWith(uploadRoot)) {
            throw new IllegalArgumentException("Invalid OCR file path");
        }
        return resolved;
    }

    public record OcrRequest(Long documentId, String fileUrl, String language) {}
    public record OcrResponse(Long documentId, String ocrText, Double confidence, String modelUsed) {}
    public record SummarizeRequest(Long documentId, String content, String summaryType) {}
    public record SummarizeResponse(Long documentId, String summaryType, String summary, Double confidence, String modelUsed) {}
    public record ClassifyRequest(Long documentId, String content) {}
    public record ClassifyResponse(Long documentId, String category, String categoryName, Double confidence, String reason) {}
    public record ExtractMetadataRequest(Long documentId, String content) {}
    public record ExtractMetadataResponse(Long documentId, Map<String, String> metadata, Double confidence, String modelUsed) {}
    public record SuggestionRequest(Long documentId, String content) {}
    public record SuggestionItem(String action, String description, String priority) {}
    public record SuggestionResponse(Long documentId, List<SuggestionItem> suggestions, Double confidence) {}
    public record IndexTrigger(String triggeredBy) {}
    public record IndexDocumentData(Long documentId, Boolean indexed, Integer totalChunks) {}
    public record IndexDocumentResponse(Boolean success, String message, IndexDocumentData data) {}
}
