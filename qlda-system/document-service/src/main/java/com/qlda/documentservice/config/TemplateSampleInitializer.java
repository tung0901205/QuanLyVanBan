package com.qlda.documentservice.config;

import com.qlda.documentservice.entity.TemplateVanBan;
import com.qlda.documentservice.repository.TemplateVanBanRepository;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Slf4j
public class TemplateSampleInitializer implements ApplicationRunner {
    private final TemplateVanBanRepository templateVanBanRepository;
    private final AppProperties appProperties;

    private static final Map<String, String> BUNDLED_TEMPLATES = new LinkedHashMap<>();
    static {
        BUNDLED_TEMPLATES.put("TPL-CV-01", "template-samples/TPL-CV-01.docx");
        BUNDLED_TEMPLATES.put("TPL-CV-02", "template-samples/TPL-CV-02.docx");
        BUNDLED_TEMPLATES.put("TPL-BB-01", "template-samples/TPL-BB-01.docx");
        BUNDLED_TEMPLATES.put("TPL-BB-02", "template-samples/TPL-BB-02.docx");
        BUNDLED_TEMPLATES.put("TPL-DX-01", "template-samples/TPL-DX-01.docx");
        BUNDLED_TEMPLATES.put("TPL-QD-01", "template-samples/TPL-QD-01.docx");
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        Path uploadDir = Paths.get(appProperties.getUploadDir()).toAbsolutePath().normalize();
        try {
            Files.createDirectories(uploadDir);
        } catch (IOException ex) {
            log.warn("Cannot prepare upload directory for bundled templates: {}", uploadDir, ex);
            return;
        }

        BUNDLED_TEMPLATES.forEach((code, resourceName) -> templateVanBanRepository.findByMaTemplate(code)
                .ifPresent(template -> ensureBundledFile(template, resourceName, uploadDir)));
    }

    private void ensureBundledFile(TemplateVanBan template, String resourceName, Path uploadDir) {
        // Luôn làm mới 6 template mẫu đi kèm ứng dụng khi service khởi động.
        // Điều này sửa cả các volume cũ đã chứa bản DOCX lỗi font/placeholder.
        String targetName = "sample-" + template.getMaTemplate() + ".docx";
        Path target = uploadDir.resolve(targetName).normalize();
        if (!target.startsWith(uploadDir)) return;

        ClassPathResource resource = new ClassPathResource(resourceName);
        try (InputStream input = resource.getInputStream()) {
            Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
            template.setTepMau("/uploads/" + targetName);
            templateVanBanRepository.save(template);
            log.info("Installed bundled template file: {} -> {}", template.getMaTemplate(), target);
        } catch (IOException ex) {
            log.warn("Cannot install bundled template file for {}", template.getMaTemplate(), ex);
        }
    }

    private boolean uploadedFileExists(String storedPath, Path uploadDir) {
        String filename = storedPath.replace("\\", "/");
        if (filename.startsWith("/uploads/")) {
            filename = filename.substring("/uploads/".length());
        }
        Path candidate = uploadDir.resolve(filename).normalize();
        return candidate.startsWith(uploadDir) && Files.exists(candidate);
    }
}
