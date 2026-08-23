package com.qlda.aiservice.service;

import com.qlda.aiservice.client.DocumentInternalApiService;
import com.qlda.aiservice.dto.internal.DocumentAttachmentDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@Slf4j
@Service
public class DocumentTextExtractionService {

    private final DocumentInternalApiService documentInternalApiService;
    private final OcrService ocrService;
    private final Path uploadRoot;

    public DocumentTextExtractionService(
        DocumentInternalApiService documentInternalApiService,
        OcrService ocrService,
        @Value("${ai.upload-root:/app/uploads}") String uploadRoot
    ) {
        this.documentInternalApiService = documentInternalApiService;
        this.ocrService = ocrService;
        this.uploadRoot = Paths.get(uploadRoot).toAbsolutePath().normalize();
    }

    public ExtractedDocumentText extractBestText(Long documentId, boolean persistOcr) {
        var content = documentInternalApiService.getDocumentContent(documentId);
        if (isUseful(content.ocrText())) {
            return new ExtractedDocumentText(content.ocrText().trim(), "OCR_SAVED", null);
        }

        List<DocumentAttachmentDto> attachments = documentInternalApiService.getDocumentAttachments(documentId).stream()
            .filter(this::isSupportedAttachment)
            .sorted(Comparator.comparingInt(this::priority))
            .toList();

        for (DocumentAttachmentDto attachment : attachments) {
            try {
                Path path = resolveUploadPath(attachment.duongDanTep());
                if (!Files.isRegularFile(path)) {
                    log.debug("Attachment file missing for OCR: documentId={} path={}", documentId, path);
                    continue;
                }
                String text = ocrService.extractText(path);
                if (isUseful(text)) {
                    String normalized = text.trim();
                    if (persistOcr) {
                        try {
                            documentInternalApiService.updateOcrContent(documentId, normalized);
                        } catch (Exception persistEx) {
                            log.warn("OCR extracted but could not persist documentId={}: {}", documentId, persistEx.getMessage());
                        }
                    }
                    return new ExtractedDocumentText(normalized, "ATTACHMENT", attachment.tenTep());
                }
            } catch (Exception ex) {
                log.warn("Could not extract attachment documentId={} file={}: {}",
                    documentId, attachment.tenTep(), ex.getMessage());
            }
        }

        if (isUseful(content.noiDung()) && !sameText(content.noiDung(), content.trichYeu())) {
            return new ExtractedDocumentText(content.noiDung().trim(), "CONTENT", null);
        }
        if (isUseful(content.trichYeu())) {
            return new ExtractedDocumentText(content.trichYeu().trim(), "TITLE", null);
        }
        return new ExtractedDocumentText("", "EMPTY", null);
    }

    public Path resolveUploadPath(String fileUrl) {
        if (fileUrl == null || fileUrl.isBlank()) {
            throw new IllegalArgumentException("Đường dẫn tệp trống");
        }
        String normalized = fileUrl.replace('\\', '/').trim();
        String filename;
        if (normalized.startsWith("/uploads/")) {
            filename = normalized.substring("/uploads/".length());
        } else {
            filename = Paths.get(normalized).getFileName().toString();
        }
        Path resolved = uploadRoot.resolve(filename).normalize();
        if (!resolved.startsWith(uploadRoot)) {
            throw new IllegalArgumentException("Đường dẫn tệp OCR không hợp lệ");
        }
        return resolved;
    }

    private boolean isSupportedAttachment(DocumentAttachmentDto attachment) {
        String name = ((attachment.tenTep() == null ? "" : attachment.tenTep()) + " "
            + (attachment.duongDanTep() == null ? "" : attachment.duongDanTep())).toLowerCase(Locale.ROOT);
        return name.matches(".*\\.(pdf|docx|txt|png|jpg|jpeg|tif|tiff|bmp|webp)(\\?.*)?$");
    }

    private int priority(DocumentAttachmentDto attachment) {
        String name = ((attachment.tenTep() == null ? "" : attachment.tenTep()) + " "
            + (attachment.duongDanTep() == null ? "" : attachment.duongDanTep())).toLowerCase(Locale.ROOT);
        if (name.contains(".docx")) return 0;
        if (name.contains(".pdf")) return 1;
        if (name.contains(".txt")) return 2;
        return 3;
    }

    private boolean isUseful(String text) {
        return text != null && text.replaceAll("\\s+", "").length() >= 20;
    }

    private boolean sameText(String a, String b) {
        if (a == null || b == null) return false;
        return a.trim().equalsIgnoreCase(b.trim());
    }

    public record ExtractedDocumentText(String text, String source, String attachmentName) { }
}
