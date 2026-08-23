package com.qlda.aiservice.service;

import java.nio.file.Path;
import org.springframework.web.multipart.MultipartFile;

public interface OcrService {
    String extractText(MultipartFile file);

    String extractText(Path filePath);
}
