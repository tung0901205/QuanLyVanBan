package com.qlda.notificationservice.report.service;

import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.qlda.notificationservice.common.exception.AppException;
import com.qlda.notificationservice.common.exception.ErrorCode;
import java.awt.Color;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ReportExportWriter {

    private static final Path VIETNAMESE_FONT =
            Path.of("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf");
    private final Path exportDirectory;

    public ReportExportWriter(@Value("${app.report-export-dir:exports}") String directory) {
        this.exportDirectory = Path.of(directory).toAbsolutePath().normalize();
    }

    public void write(String format, String fileName, String title, List<List<String>> rows) {
        ensureDirectory();
        cleanupExpiredFiles();
        Path target = resolve(fileName);
        if ("pdf".equals(format)) {
            writePdf(target, title, rows);
        } else {
            writeExcel(target, title, rows);
        }
    }

    public Path resolveExisting(String fileName) {
        Path path = resolve(fileName);
        if (!Files.isRegularFile(path)) {
            throw new AppException(ErrorCode.INVALID_REQUEST);
        }
        return path;
    }

    private void writeExcel(Path target, String title, List<List<String>> rows) {
        try (Workbook workbook = new XSSFWorkbook();
             OutputStream output = Files.newOutputStream(target, StandardOpenOption.CREATE,
                     StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
            Sheet sheet = workbook.createSheet("Báo cáo");
            org.apache.poi.ss.usermodel.Font titleFont = workbook.createFont();
            titleFont.setBold(true);
            titleFont.setFontHeightInPoints((short) 16);
            CellStyle titleStyle = workbook.createCellStyle();
            titleStyle.setFont(titleFont);
            Row titleRow = sheet.createRow(0);
            titleRow.createCell(0).setCellValue(title);
            titleRow.getCell(0).setCellStyle(titleStyle);

            CellStyle headerStyle = workbook.createCellStyle();
            org.apache.poi.ss.usermodel.Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerFont.setColor(IndexedColors.WHITE.getIndex());
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
                Row row = sheet.createRow(rowIndex + 2);
                List<String> values = rows.get(rowIndex);
                for (int column = 0; column < values.size(); column++) {
                    row.createCell(column).setCellValue(values.get(column) == null ? "" : values.get(column));
                    if (rowIndex == 0) row.getCell(column).setCellStyle(headerStyle);
                }
            }
            int columns = rows.stream().mapToInt(List::size).max().orElse(2);
            for (int column = 0; column < columns; column++) {
                sheet.autoSizeColumn(column);
                sheet.setColumnWidth(column, Math.min(sheet.getColumnWidth(column) + 768, 16000));
            }
            workbook.write(output);
        } catch (IOException exception) {
            throw new AppException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    private void writePdf(Path target, String title, List<List<String>> rows) {
        Document document = new Document(PageSize.A4.rotate(), 30, 30, 32, 32);
        try (OutputStream output = Files.newOutputStream(target, StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
            PdfWriter.getInstance(document, output);
            BaseFont baseFont = Files.isRegularFile(VIETNAMESE_FONT)
                    ? BaseFont.createFont(VIETNAMESE_FONT.toString(), BaseFont.IDENTITY_H, BaseFont.EMBEDDED)
                    : BaseFont.createFont(BaseFont.HELVETICA, BaseFont.WINANSI, BaseFont.NOT_EMBEDDED);
            Font titleFont = new Font(baseFont, 16, Font.BOLD, new Color(15, 40, 80));
            Font normalFont = new Font(baseFont, 9, Font.NORMAL, Color.DARK_GRAY);
            Font headerFont = new Font(baseFont, 9, Font.BOLD, Color.WHITE);
            document.open();
            com.lowagie.text.Paragraph heading = new com.lowagie.text.Paragraph(title, titleFont);
            heading.setAlignment(Element.ALIGN_CENTER);
            heading.setSpacingAfter(18);
            document.add(heading);

            int columns = rows.stream().mapToInt(List::size).max().orElse(2);
            PdfPTable table = new PdfPTable(columns);
            table.setWidthPercentage(100);
            for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
                List<String> row = rows.get(rowIndex);
                for (int column = 0; column < columns; column++) {
                    String value = column < row.size() && row.get(column) != null ? row.get(column) : "";
                    PdfPCell cell = new PdfPCell(new Phrase(value, rowIndex == 0 ? headerFont : normalFont));
                    cell.setPadding(6);
                    if (rowIndex == 0) cell.setBackgroundColor(new Color(30, 64, 175));
                    table.addCell(cell);
                }
            }
            document.add(table);
            document.close();
        } catch (Exception exception) {
            throw new AppException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    private void ensureDirectory() {
        try {
            Files.createDirectories(exportDirectory);
        } catch (IOException exception) {
            throw new AppException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    private Path resolve(String fileName) {
        if (fileName == null || !fileName.matches("[a-zA-Z0-9._-]+")) {
            throw new AppException(ErrorCode.INVALID_REQUEST);
        }
        Path resolved = exportDirectory.resolve(fileName).normalize();
        if (!resolved.startsWith(exportDirectory)) {
            throw new AppException(ErrorCode.INVALID_REQUEST);
        }
        return resolved;
    }

    private void cleanupExpiredFiles() {
        Instant cutoff = Instant.now().minus(Duration.ofDays(7));
        try (var files = Files.list(exportDirectory)) {
            files.filter(Files::isRegularFile).forEach(path -> {
                try {
                    if (Files.getLastModifiedTime(path).toInstant().isBefore(cutoff)) Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // A stale export must never prevent creation of a new report.
                }
            });
        } catch (IOException ignored) {
            // The write operation below reports a useful error if the directory is unusable.
        }
    }
}
