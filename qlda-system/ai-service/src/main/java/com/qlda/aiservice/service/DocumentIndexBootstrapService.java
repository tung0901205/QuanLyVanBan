package com.qlda.aiservice.service;

import com.qlda.aiservice.client.DocumentInternalApiService;
import com.qlda.aiservice.dto.internal.DocumentSearchDto;
import com.qlda.aiservice.dto.request.IndexDocumentRequest;
import com.qlda.aiservice.repository.AiDocumentChunkRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * V4.1 could seed guide vectors but its internal AI calls were rejected by the
 * JWT filter, therefore existing documents often had no vector index at all.
 * This lightweight bootstrap indexes missing documents once after startup.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentIndexBootstrapService {

    private final DocumentInternalApiService documentInternalApiService;
    private final AiDocumentChunkRepository aiDocumentChunkRepository;
    private final AiApplicationService aiApplicationService;

    @EventListener(ApplicationReadyEvent.class)
    public void indexMissingDocuments() {
        try {
            List<DocumentSearchDto> documents = documentInternalApiService.listDocumentsForAiIndex(500);
            int indexed = 0;
            for (DocumentSearchDto document : documents) {
                if (document.id() == null || document.id() <= 0 || aiDocumentChunkRepository.existsByVanBanId(document.id())) {
                    continue;
                }
                try {
                    var content = documentInternalApiService.getDocumentContent(document.id());
                    String text = firstNonBlank(content.ocrText(), content.noiDung(), content.trichYeu(), document.trichYeu());
                    if (text == null || text.isBlank()) continue;
                    Map<String, Object> metadata = new LinkedHashMap<>();
                    metadata.put("type", "van_ban");
                    metadata.put("soKyHieu", document.soKyHieu());
                    metadata.put("trichYeu", document.trichYeu());
                    metadata.put("tenLoaiVanBan", document.tenLoaiVanBan());
                    metadata.put("trangThai", document.trangThai());
                    aiApplicationService.indexDocument(new IndexDocumentRequest(document.id(), null, text, metadata));
                    indexed++;
                } catch (Exception ex) {
                    log.warn("Skip AI index document {}: {}", document.id(), ex.getMessage());
                }
            }
            log.info("AI document bootstrap completed: {} new document(s) indexed", indexed);
        } catch (Exception ex) {
            log.warn("AI document bootstrap skipped: {}", ex.getMessage());
        }
    }

    private String firstNonBlank(String... values) {
        if (values == null) return null;
        for (String value : values) {
            if (value != null && !value.isBlank()) return value.trim();
        }
        return null;
    }
}
