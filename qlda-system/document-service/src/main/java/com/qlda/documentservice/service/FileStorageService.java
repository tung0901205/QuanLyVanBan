package com.qlda.documentservice.service;

import java.util.Map;
import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

public interface FileStorageService {
    String store(MultipartFile file);

    Resource load(String path);

    String copy(String path);

    String copyAndReplace(String path, Map<String, String> replaceData);

    void delete(String path);

    String buildFileUrl(String filename);
}

