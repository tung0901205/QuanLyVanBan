package com.qlda.documentservice.config;

import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
@Getter
@Setter
public class AppProperties {
    private String uploadDir = "uploads";
    private long maxFileSizeBytes = 104_857_600L;
    private List<String> allowedFileExtensions = List.of(
        "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx",
        "odt", "ods", "odp", "txt", "csv", "xml",
        "png", "jpg", "jpeg", "tif", "tiff"
    );
}
