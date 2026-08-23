package com.qlda.aiservice.service;

import com.qlda.aiservice.exception.AppException;
import com.qlda.aiservice.exception.ErrorCode;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import java.util.zip.ZipFile;
import javax.xml.parsers.DocumentBuilderFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

@Slf4j
@Service
@Primary
public class TesseractOcrService implements OcrService {

    private static final long PROCESS_TIMEOUT_SECONDS = 180;
    private static final int MIN_USEFUL_TEXT = 25;
    private static final List<String> SUPPORTED = List.of(
        "pdf", "docx", "txt", "csv", "json", "xml", "md",
        "png", "jpg", "jpeg", "tif", "tiff", "bmp", "webp"
    );

    @Override
    public String extractText(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new AppException(ErrorCode.INVALID_FILE_FORMAT, HttpStatus.BAD_REQUEST, "Tệp OCR không được để trống");
        }
        String originalName = file.getOriginalFilename() == null ? "upload.bin" : file.getOriginalFilename();
        String extension = getExtension(originalName);
        Path workDir = null;
        try {
            workDir = Files.createTempDirectory("qlda-ocr-upload-");
            Path inputFile = workDir.resolve("input" + (extension.isBlank() ? "" : "." + extension));
            file.transferTo(inputFile);
            return extractText(inputFile);
        } catch (AppException exception) {
            throw exception;
        } catch (Exception exception) {
            log.error("OCR failed for file={}", originalName, exception);
            throw new AppException(ErrorCode.OCR_FAILED, HttpStatus.INTERNAL_SERVER_ERROR,
                "Không thể đọc nội dung tệp: " + safeMessage(exception), exception);
        } finally {
            deleteRecursively(workDir);
        }
    }

    @Override
    public String extractText(Path filePath) {
        if (filePath == null || !Files.exists(filePath) || !Files.isRegularFile(filePath)) {
            throw new AppException(ErrorCode.INVALID_FILE_FORMAT, HttpStatus.BAD_REQUEST, "Không tìm thấy tệp cần đọc");
        }
        String extension = getExtension(filePath.getFileName().toString());
        if (!SUPPORTED.contains(extension)) {
            throw new AppException(ErrorCode.INVALID_FILE_FORMAT, HttpStatus.BAD_REQUEST,
                "Định dạng ." + extension + " chưa được hỗ trợ OCR");
        }

        Path workDir = null;
        try {
            String result;
            if (isPlainText(extension)) {
                result = Files.readString(filePath, StandardCharsets.UTF_8);
            } else if ("docx".equals(extension)) {
                result = extractDocx(filePath);
            } else if ("pdf".equals(extension)) {
                workDir = Files.createTempDirectory("qlda-ocr-pdf-");
                result = extractPdf(filePath, workDir);
            } else {
                result = runTesseractWithFallback(filePath);
            }

            result = normalizeText(result);
            if (!isUseful(result)) {
                throw new AppException(ErrorCode.OCR_FAILED, HttpStatus.UNPROCESSABLE_ENTITY,
                    "Không nhận dạng được nội dung chữ trong tệp");
            }
            return result;
        } catch (AppException exception) {
            throw exception;
        } catch (Exception exception) {
            log.error("OCR failed for path={}", filePath, exception);
            throw new AppException(ErrorCode.OCR_FAILED, HttpStatus.INTERNAL_SERVER_ERROR,
                "Không thể đọc nội dung tệp: " + safeMessage(exception), exception);
        } finally {
            deleteRecursively(workDir);
        }
    }

    /** DOCX is text-based; parse Word XML directly instead of running OCR. */
    private String extractDocx(Path docxFile) throws Exception {
        StringBuilder result = new StringBuilder();
        try (ZipFile zipFile = new ZipFile(docxFile.toFile(), StandardCharsets.UTF_8)) {
            var entries = zipFile.stream()
                .filter(entry -> !entry.isDirectory())
                .filter(entry -> entry.getName().equals("word/document.xml")
                    || entry.getName().matches("word/header\\d+\\.xml")
                    || entry.getName().matches("word/footer\\d+\\.xml"))
                .sorted(Comparator.comparing(entry -> entry.getName().equals("word/document.xml") ? "0" : "1" + entry.getName()))
                .toList();

            for (var entry : entries) {
                try (InputStream input = zipFile.getInputStream(entry)) {
                    String part = parseWordXml(input);
                    if (!part.isBlank()) {
                        if (!result.isEmpty()) result.append("\n");
                        result.append(part);
                    }
                }
            }
        }
        return result.toString();
    }

    private String parseWordXml(InputStream input) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        var document = factory.newDocumentBuilder().parse(input);
        StringBuilder out = new StringBuilder();
        appendWordNode(document.getDocumentElement(), out);
        return out.toString();
    }

    private void appendWordNode(Node node, StringBuilder out) {
        String local = node.getLocalName();
        if ("t".equals(local)) {
            out.append(node.getTextContent());
            return;
        }
        if ("tab".equals(local)) {
            out.append('\t');
            return;
        }
        if ("br".equals(local) || "cr".equals(local)) {
            out.append('\n');
            return;
        }

        NodeList children = node.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            appendWordNode(children.item(i), out);
        }
        if ("p".equals(local) || "tr".equals(local)) {
            out.append('\n');
        } else if ("tc".equals(local)) {
            out.append('\t');
        }
    }

    private String extractPdf(Path pdfFile, Path workDir) throws IOException, InterruptedException {
        // Digital PDFs should be read directly. This is faster and more accurate than OCR.
        String directText = runCommandAllowFailure(List.of("pdftotext", "-layout", "-enc", "UTF-8", pdfFile.toString(), "-"));
        directText = normalizeText(directText);
        if (isUseful(directText)) {
            return directText;
        }

        // Scanned PDF: rasterize pages then OCR Vietnamese + English.
        Path outputPrefix = workDir.resolve("page");
        runCommand(List.of("pdftoppm", "-png", "-r", "250", pdfFile.toString(), outputPrefix.toString()));

        StringBuilder result = new StringBuilder();
        try (Stream<Path> files = Files.list(workDir)) {
            List<Path> pages = files
                .filter(path -> path.getFileName().toString().startsWith("page-"))
                .filter(path -> path.getFileName().toString().endsWith(".png"))
                .sorted()
                .toList();
            for (Path page : pages) {
                String pageText = runTesseractWithFallback(page);
                if (isUseful(pageText)) {
                    if (!result.isEmpty()) result.append("\n\n");
                    result.append(pageText.trim());
                }
            }
        }
        return result.toString();
    }

    private String runTesseractWithFallback(Path imageFile) throws IOException, InterruptedException {
        String primary = runCommandAllowFailure(List.of(
            "tesseract", imageFile.toString(), "stdout", "-l", "vie+eng", "--oem", "1", "--psm", "6"
        ));
        if (isUseful(primary)) return primary;
        return runCommand(List.of(
            "tesseract", imageFile.toString(), "stdout", "-l", "vie+eng", "--oem", "1", "--psm", "3"
        ));
    }

    private String runCommand(List<String> command) throws IOException, InterruptedException {
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        boolean finished = process.waitFor(PROCESS_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            throw new IOException("Tiến trình OCR bị timeout");
        }
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (process.exitValue() != 0) {
            throw new IOException("Lệnh xử lý tệp lỗi (exit=" + process.exitValue() + "): " + output);
        }
        return output;
    }

    private String runCommandAllowFailure(List<String> command) throws IOException, InterruptedException {
        try {
            return runCommand(command);
        } catch (IOException ex) {
            log.debug("Optional OCR command failed: {}", ex.getMessage());
            return "";
        }
    }

    private boolean isPlainText(String extension) {
        return List.of("txt", "csv", "json", "xml", "md").contains(extension);
    }

    private boolean isUseful(String text) {
        if (text == null) return false;
        String compact = text.replaceAll("\\s+", "");
        return compact.length() >= MIN_USEFUL_TEXT;
    }

    private String normalizeText(String text) {
        if (text == null) return "";
        return text
            .replace('\u0000', ' ')
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .replaceAll("[ \\t]+", " ")
            .replaceAll(" *\\n *", "\n")
            .replaceAll("\\n{3,}", "\n\n")
            .trim();
    }

    private String getExtension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) return "";
        return fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private String safeMessage(Exception exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? exception.getClass().getSimpleName() : message;
    }

    private void deleteRecursively(Path directory) {
        if (directory == null || !Files.exists(directory)) return;
        try (Stream<Path> stream = Files.walk(directory)) {
            stream.sorted(Comparator.reverseOrder()).forEach(path -> {
                try { Files.deleteIfExists(path); } catch (IOException ignored) { }
            });
        } catch (IOException ignored) { }
    }
}
