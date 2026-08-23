package com.qlda.notificationservice.report.service;

import com.qlda.notificationservice.client.AuthServiceClient;
import com.qlda.notificationservice.client.DocumentServiceClient;
import com.qlda.notificationservice.client.WorkflowServiceClient;
import com.qlda.notificationservice.client.dto.AuthUserResponse;
import com.qlda.notificationservice.client.dto.DocumentOverduePageResponse;
import com.qlda.notificationservice.client.dto.DocumentStatisticsClientResponse;
import com.qlda.notificationservice.client.dto.WorkflowProgressClientItem;
import com.qlda.notificationservice.client.dto.WorkflowProgressClientResponse;
import com.qlda.notificationservice.client.dto.WorkflowStatisticsClientResponse;
import com.qlda.notificationservice.common.api.PageResponse;
import com.qlda.notificationservice.common.exception.AppException;
import com.qlda.notificationservice.common.exception.ErrorCode;
import com.qlda.notificationservice.report.dto.DashboardResponse;
import com.qlda.notificationservice.report.dto.DocumentStatisticsResponse;
import com.qlda.notificationservice.report.dto.ExportReportResponse;
import com.qlda.notificationservice.report.dto.OverdueDocumentItem;
import com.qlda.notificationservice.report.dto.StatisticItem;
import com.qlda.notificationservice.report.dto.WorkflowProgressItem;
import com.qlda.notificationservice.report.dto.WorkflowProgressResponse;
import java.time.LocalDate;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class ReportService {

    private static final Set<String> ALLOWED_GROUP_BY = Set.of("status", "type", "unit", "month");
    private static final Set<String> ALLOWED_REPORT_TYPES = Set.of(
        "dashboard",
        "document_statistics",
        "workflow_progress",
        "overdue_documents"
    );
    private static final Set<String> ALLOWED_EXPORT_FORMATS = Set.of("excel", "xlsx", "pdf");

    private final DocumentServiceClient documentServiceClient;
    private final WorkflowServiceClient workflowServiceClient;
    private final AuthServiceClient authServiceClient;
    private final ReportExportWriter reportExportWriter;

    public ReportService(
        DocumentServiceClient documentServiceClient,
        WorkflowServiceClient workflowServiceClient,
        AuthServiceClient authServiceClient,
        ReportExportWriter reportExportWriter
    ) {
        this.documentServiceClient = documentServiceClient;
        this.workflowServiceClient = workflowServiceClient;
        this.authServiceClient = authServiceClient;
        this.reportExportWriter = reportExportWriter;
    }

    public DashboardResponse getDashboard(String fromDate, String toDate, Long donViId) {
        DocumentStatisticsClientResponse documentStatistics = loadDocumentStatisticsSafely(
            fromDate, toDate, donViId, "status"
        );
        WorkflowStatisticsClientResponse workflowStatistics = loadWorkflowStatisticsSafely(
            fromDate, toDate, donViId
        );

        int totalDocuments = safeInt(documentStatistics.totalDocuments());
        int incomingDocuments = safeInt(documentStatistics.incomingDocuments());
        int outgoingDocuments = safeInt(documentStatistics.outgoingDocuments());
        int completedDocuments = safeInt(workflowStatistics.completedTasks());
        int processingDocuments = safeInt(workflowStatistics.processingTasks());
        int overdueDocuments = safeInt(workflowStatistics.overdueTasks());
        int totalTasks = safeInt(workflowStatistics.totalTasks());
        double completionRate = calculateRate(completedDocuments, totalTasks);
        double overdueRate = calculateRate(overdueDocuments, totalTasks);

        return new DashboardResponse(
            totalDocuments,
            incomingDocuments,
            outgoingDocuments,
            completedDocuments,
            processingDocuments,
            overdueDocuments,
            completionRate,
            overdueRate
        );
    }

    public DocumentStatisticsResponse getDocumentStatistics(String fromDate, String toDate, Long donViId, String groupBy) {
        String normalizedGroupBy = normalizeGroupBy(groupBy);
        DocumentStatisticsClientResponse clientResponse = loadDocumentStatisticsSafely(
            fromDate, toDate, donViId, normalizedGroupBy
        );
        List<StatisticItem> items = clientResponse.items() == null ? List.of() : clientResponse.items().stream()
            .map(item -> new StatisticItem(item.label(), safeInt(item.value())))
            .toList();
        return new DocumentStatisticsResponse(normalizedGroupBy, items);
    }

    public WorkflowProgressResponse getWorkflowProgress(String fromDate, String toDate, Long donViId, Long nguoiXuLyId) {
        try {
            WorkflowProgressClientResponse clientResponse = workflowServiceClient
                .getWorkflowProgress(fromDate, toDate, donViId, nguoiXuLyId);
            List<WorkflowProgressItem> mappedItems = clientResponse.items() == null ? List.of() : clientResponse.items().stream()
                .map(this::mapWorkflowItem)
                .toList();
            int total = safeInt(clientResponse.totalTasks());
            int completed = safeInt(clientResponse.completedTasks());
            int processing = safeInt(clientResponse.processingTasks());
            java.time.LocalDateTime now = java.time.LocalDateTime.now();
            int overdue = (int) mappedItems.stream()
                .filter(item -> Integer.valueOf(1).equals(item.trangThaiXuLy()))
                .filter(item -> item.hanXuLy() != null && item.hanXuLy().isBefore(now))
                .count();
            return new WorkflowProgressResponse(total, completed, processing, overdue, mappedItems);
        } catch (RuntimeException ex) {
            throw new AppException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    public PageResponse<OverdueDocumentItem> getOverdueDocuments(Long donViId, Long nguoiXuLyId, int page, int size) {
        try {
            DocumentOverduePageResponse clientResponse = documentServiceClient
                .getOverdueDocuments(donViId, nguoiXuLyId, page, size);
            List<OverdueDocumentItem> items = clientResponse.content() == null ? List.of() : clientResponse.content().stream()
                .map(item -> new OverdueDocumentItem(
                    item.documentId(),
                    item.soKyHieu(),
                    item.trichYeu(),
                    item.nguoiXuLyId(),
                    resolveUserName(item.nguoiXuLyId()),
                    item.hanXuLy(),
                    item.soNgayTre(),
                    item.trangThai()
                ))
                .toList();
            int responsePage = clientResponse.page() == null ? page : clientResponse.page();
            int responseSize = clientResponse.size() == null ? size : clientResponse.size();
            long totalElements = clientResponse.totalElements() == null ? items.size() : clientResponse.totalElements();
            int totalPages = responseSize == 0 ? 1 : (int) Math.ceil((double) totalElements / responseSize);
            return new PageResponse<>(items, responsePage, responseSize, totalElements, totalPages);
        } catch (RuntimeException ex) {
            throw new AppException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    public ExportReportResponse export(String reportType, String format, String fromDate, String toDate, Long donViId) {
        String normalizedReportType = normalizeReportType(reportType);
        String normalizedFormat = normalizeFormat(format);
        String fileExt = "pdf".equals(normalizedFormat) ? "pdf" : "xlsx";
        String now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        String fileName = "bao-cao-" + normalizedReportType + "-" + now + "." + fileExt;
        ExportData exportData = buildExportData(normalizedReportType, fromDate, toDate, donViId);
        reportExportWriter.write(normalizedFormat, fileName, exportData.title(), exportData.rows());
        return new ExportReportResponse(fileName, "/api/reports/exports/" + fileName);
    }

    public Path getExportFile(String fileName) {
        return reportExportWriter.resolveExisting(fileName);
    }

    private ExportData buildExportData(String reportType, String fromDate, String toDate, Long donViId) {
        List<List<String>> rows = new ArrayList<>();
        return switch (reportType) {
            case "dashboard" -> {
                DashboardResponse dashboard = getDashboard(fromDate, toDate, donViId);
                rows.add(List.of("Chỉ số", "Giá trị"));
                rows.add(List.of("Tổng văn bản", String.valueOf(dashboard.totalDocuments())));
                rows.add(List.of("Văn bản đến", String.valueOf(dashboard.incomingDocuments())));
                rows.add(List.of("Văn bản đi", String.valueOf(dashboard.outgoingDocuments())));
                rows.add(List.of("Đã hoàn thành", String.valueOf(dashboard.completedDocuments())));
                rows.add(List.of("Đang xử lý", String.valueOf(dashboard.processingDocuments())));
                rows.add(List.of("Quá hạn", String.valueOf(dashboard.overdueDocuments())));
                rows.add(List.of("Tỷ lệ hoàn thành (%)", String.valueOf(dashboard.completionRate())));
                yield new ExportData("Báo cáo tổng quan", rows);
            }
            case "workflow_progress" -> {
                WorkflowProgressResponse progress = getWorkflowProgress(fromDate, toDate, donViId, null);
                rows.add(List.of("Số ký hiệu", "Trích yếu", "Người xử lý", "Tiến độ (%)", "Hạn xử lý"));
                progress.items().forEach(item -> rows.add(List.of(
                        safeText(item.soKyHieu()), safeText(item.trichYeu()), safeText(item.nguoiXuLy()),
                        String.valueOf(item.tyLeHoanThanh() == null ? 0 : item.tyLeHoanThanh()),
                        safeText(item.hanXuLy()))));
                yield new ExportData("Báo cáo tiến độ quy trình", rows);
            }
            case "overdue_documents" -> {
                PageResponse<OverdueDocumentItem> overdue = getOverdueDocuments(donViId, null, 0, 5000);
                rows.add(List.of("Số ký hiệu", "Trích yếu", "Người xử lý", "Hạn xử lý", "Số ngày trễ"));
                overdue.content().forEach(item -> rows.add(List.of(
                        safeText(item.soKyHieu()), safeText(item.trichYeu()), safeText(item.nguoiXuLy()),
                        safeText(item.hanXuLy()), String.valueOf(item.soNgayTre() == null ? 0 : item.soNgayTre()))));
                yield new ExportData("Báo cáo văn bản quá hạn", rows);
            }
            default -> {
                DocumentStatisticsResponse statistics = getDocumentStatistics(fromDate, toDate, donViId, "status");
                rows.add(List.of("Nhóm trạng thái", "Số lượng"));
                statistics.items().forEach(item -> rows.add(List.of(
                        safeText(item.label()), String.valueOf(item.value()))));
                yield new ExportData("Báo cáo thống kê văn bản", rows);
            }
        };
    }

    private String safeText(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private WorkflowProgressItem mapWorkflowItem(WorkflowProgressClientItem item) {
        return new WorkflowProgressItem(
            item.documentId(),
            item.soKyHieu(),
            item.trichYeu(),
            item.nguoiXuLyId(),
            resolveUserName(item.nguoiXuLyId()),
            item.trangThaiXuLy(),
            item.tyLeHoanThanh(),
            item.hanXuLy()
        );
    }

    private String resolveUserName(Long nguoiXuLyId) {
        if (nguoiXuLyId == null) {
            return null;
        }
        try {
            AuthUserResponse user = authServiceClient.getUserById(nguoiXuLyId);
            if (user != null && user.hoTen() != null) {
                return user.hoTen();
            }
        } catch (RuntimeException ignored) {
            // TODO log auth-service enrich failure in centralized logging.
        }
        return String.valueOf(nguoiXuLyId);
    }

    private String normalizeGroupBy(String groupBy) {
        String value = groupBy == null ? "status" : groupBy.trim().toLowerCase(Locale.ROOT);
        if (!ALLOWED_GROUP_BY.contains(value)) {
            throw new AppException(ErrorCode.INVALID_REQUEST);
        }
        return "xlsx".equals(value) ? "excel" : value;
    }

    private String normalizeReportType(String reportType) {
        String value = reportType == null ? "dashboard" : reportType.trim().toLowerCase(Locale.ROOT);
        if (!ALLOWED_REPORT_TYPES.contains(value)) {
            throw new AppException(ErrorCode.INVALID_REQUEST);
        }
        return value;
    }

    private String normalizeFormat(String format) {
        String value = format == null ? "excel" : format.trim().toLowerCase(Locale.ROOT);
        if (!ALLOWED_EXPORT_FORMATS.contains(value)) {
            throw new AppException(ErrorCode.INVALID_REQUEST);
        }
        return value;
    }

    private DocumentStatisticsClientResponse loadDocumentStatisticsSafely(
        String fromDate,
        String toDate,
        Long donViId,
        String groupBy
    ) {
        try {
            return documentServiceClient.getDocumentStatistics(fromDate, toDate, donViId, groupBy);
        } catch (RuntimeException ex) {
            log.error("Unable to load document statistics; returning an empty dashboard section", ex);
            return new DocumentStatisticsClientResponse(0L, 0L, 0L, List.of());
        }
    }

    private WorkflowStatisticsClientResponse loadWorkflowStatisticsSafely(
        String fromDate,
        String toDate,
        Long donViId
    ) {
        try {
            return workflowServiceClient.getWorkflowStatistics(fromDate, toDate, donViId);
        } catch (RuntimeException ex) {
            log.error("Unable to load workflow statistics; returning an empty dashboard section", ex);
            return new WorkflowStatisticsClientResponse(0L, 0L, 0L, 0L);
        }
    }

    private int safeInt(Number value) {
        if (value == null) return 0;
        long longValue = value.longValue();
        if (longValue > Integer.MAX_VALUE) return Integer.MAX_VALUE;
        if (longValue < Integer.MIN_VALUE) return Integer.MIN_VALUE;
        return (int) longValue;
    }

    private double calculateRate(int value, int total) {
        if (total <= 0) {
            return 0;
        }
        double rate = (double) value * 100 / total;
        return Math.round(rate * 100.0) / 100.0;
    }

    private record ExportData(String title, List<List<String>> rows) {
    }
}
