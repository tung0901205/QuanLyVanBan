package com.qlda.documentservice.service.impl;

import com.qlda.documentservice.config.AppProperties;
import com.qlda.documentservice.exception.BusinessException;
import com.qlda.documentservice.exception.ErrorCode;
import com.qlda.documentservice.service.FileStorageService;
import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import java.util.UUID;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class LocalFileStorageService implements FileStorageService {
    private final Path uploadPath;
    private final long maxFileSizeBytes;
    private final Set<String> allowedExtensions;

    public LocalFileStorageService(AppProperties appProperties) {
        this.uploadPath = Paths.get(appProperties.getUploadDir()).toAbsolutePath().normalize();
        this.maxFileSizeBytes = appProperties.getMaxFileSizeBytes();
        this.allowedExtensions = appProperties.getAllowedFileExtensions().stream()
            .map(value -> value.toLowerCase(Locale.ROOT).replace(".", "").trim())
            .filter(value -> !value.isBlank())
            .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    @Override
    public String store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw BusinessException.badRequest(ErrorCode.FILE_UPLOAD_FAILED, "File is empty");
        }
        if (file.getSize() > maxFileSizeBytes) {
            throw BusinessException.badRequest(ErrorCode.FILE_UPLOAD_FAILED, "File exceeds the configured upload limit");
        }
        try {
            String extension = validateAndGetExtension(file.getOriginalFilename());
            String storedName = newShardedName(extension);
            Path targetPath = resolveForWrite(storedName);
            writeAtomically(file.getInputStream(), targetPath);
            return buildFileUrl(storedName);
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.FILE_UPLOAD_FAILED, "Upload file failed", org.springframework.http.HttpStatus.BAD_REQUEST);
        }
    }

    @Override
    public Resource load(String path) {
        Path filePath = resolveStoredPath(path);
        if (!Files.isRegularFile(filePath) || !Files.isReadable(filePath)) {
            throw new BusinessException(ErrorCode.ATTACHMENT_NOT_FOUND, "File not found: " + path, HttpStatus.NOT_FOUND);
        }
        return new FileSystemResource(filePath);
    }

    @Override
    public String copy(String path) {
        Resource source = load(path);
        try {
            String original = source.getFilename();
            String extension = extensionWithDot(original);
            String storedName = newShardedName(extension);
            Path targetPath = resolveForWrite(storedName);
            try (var input = source.getInputStream()) {
                writeAtomically(input, targetPath);
            }
            return buildFileUrl(storedName);
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.FILE_UPLOAD_FAILED, "Copy template file failed", HttpStatus.BAD_REQUEST);
        }
    }


    @Override
    public String copyAndReplace(String path, Map<String, String> replaceData) {
        Resource source = load(path);
        String original = source.getFilename();
        String extension = "";
        if (original != null && original.contains(".")) {
            extension = original.substring(original.lastIndexOf("."));
        }
        if (!".docx".equalsIgnoreCase(extension)) {
            return copy(path);
        }
        try {
            String storedName = newShardedName(extension);
            Path targetPath = resolveForWrite(storedName);
            Path tempPath = Files.createTempFile(targetPath.getParent(), ".upload-", ".tmp");
            try (var rawInput = source.getInputStream();
                 var zipInput = new ZipInputStream(rawInput);
                 var rawOutput = Files.newOutputStream(tempPath);
                 var zipOutput = new ZipOutputStream(rawOutput)) {
                ZipEntry entry;
                int entryCount = 0;
                long totalBytes = 0;
                while ((entry = zipInput.getNextEntry()) != null) {
                    if (++entryCount > 10_000) throw new IOException("DOCX contains too many entries");
                    ZipEntry outEntry = new ZipEntry(entry.getName());
                    zipOutput.putNextEntry(outEntry);
                    byte[] data = readEntryLimited(zipInput, maxFileSizeBytes - totalBytes);
                    totalBytes += data.length;
                    if (entry.getName().startsWith("word/") && entry.getName().endsWith(".xml")) {
                        String xml = new String(data, StandardCharsets.UTF_8);
                        if (replaceData != null) {
                            for (Map.Entry<String, String> replacement : replaceData.entrySet()) {
                                String key = replacement.getKey();
                                String value = escapeXml(replacement.getValue() == null ? "" : replacement.getValue());
                                xml = xml.replace("{{" + key + "}}", value);
                                xml = xml.replace("${" + key + "}", value);
                            }
                        }
                        data = xml.getBytes(StandardCharsets.UTF_8);
                    }
                    zipOutput.write(data);
                    zipOutput.closeEntry();
                    zipInput.closeEntry();
                }
            }
            moveAtomically(tempPath, targetPath);
            return buildFileUrl(storedName);
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.FILE_UPLOAD_FAILED, "Create document from template failed", HttpStatus.BAD_REQUEST);
        }
    }

    private String escapeXml(String value) {
        return value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;");
    }

    @Override
    public void delete(String path) {
        if (path == null || path.isBlank()) {
            return;
        }
        Path targetPath = resolveStoredPath(path);
        try {
            Files.deleteIfExists(targetPath);
        } catch (IOException ignored) {
            // ignore delete error because DB state is authoritative
        }
    }

    @Override
    public String buildFileUrl(String filename) {
        return "/uploads/" + filename.replace('\\', '/');
    }

    private String validateAndGetExtension(String originalFilename) {
        String safeName = originalFilename == null ? "" : Paths.get(originalFilename).getFileName().toString();
        String extension = extensionWithDot(safeName);
        String normalized = extension.replace(".", "").toLowerCase(Locale.ROOT);
        if (normalized.isBlank() || !allowedExtensions.contains(normalized)) {
            throw BusinessException.badRequest(ErrorCode.FILE_UPLOAD_FAILED, "File type is not allowed");
        }
        return extension.toLowerCase(Locale.ROOT);
    }

    private String extensionWithDot(String filename) {
        if (filename == null) return "";
        int index = filename.lastIndexOf('.');
        return index > -1 ? filename.substring(index) : "";
    }

    private String newShardedName(String extension) {
        String id = UUID.randomUUID().toString().replace("-", "");
        return id.substring(0, 2) + "/" + id.substring(2, 4) + "/" + id + extension;
    }

    private Path resolveForWrite(String relativePath) throws IOException {
        Path target = resolveStoredPath(relativePath);
        Files.createDirectories(target.getParent());
        return target;
    }

    private Path resolveStoredPath(String path) {
        if (path == null || path.isBlank()) {
            throw BusinessException.badRequest(ErrorCode.FILE_UPLOAD_FAILED, "File path is empty");
        }
        String relative = path.replace('\\', '/').trim();
        if (relative.startsWith("/uploads/")) relative = relative.substring("/uploads/".length());
        while (relative.startsWith("/")) relative = relative.substring(1);
        Path target = uploadPath.resolve(relative).normalize();
        if (!target.startsWith(uploadPath)) {
            throw BusinessException.badRequest(ErrorCode.FILE_UPLOAD_FAILED, "Invalid file path");
        }
        return target;
    }

    private void writeAtomically(InputStream input, Path target) throws IOException {
        Path temp = Files.createTempFile(target.getParent(), ".upload-", ".tmp");
        boolean moved = false;
        try {
            Files.copy(input, temp, StandardCopyOption.REPLACE_EXISTING);
            if (Files.size(temp) > maxFileSizeBytes) throw new IOException("File exceeds configured limit");
            moveAtomically(temp, target);
            moved = true;
        } finally {
            if (!moved) Files.deleteIfExists(temp);
        }
    }

    private void moveAtomically(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ex) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private byte[] readEntryLimited(InputStream input, long remaining) throws IOException {
        if (remaining <= 0) throw new IOException("Expanded DOCX exceeds configured limit");
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[16_384];
        int read;
        long count = 0;
        while ((read = input.read(buffer)) != -1) {
            count += read;
            if (count > remaining) throw new IOException("Expanded DOCX exceeds configured limit");
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }
}
