package com.qlda.documentservice.service.impl;

import com.qlda.documentservice.common.DocumentConstants;
import com.qlda.documentservice.dto.internal.InternalDocumentRequests;
import com.qlda.documentservice.dto.internal.InternalDocumentResponses;
import com.qlda.documentservice.entity.TepDinhKem;
import com.qlda.documentservice.entity.VanBan;
import com.qlda.documentservice.exception.BusinessException;
import com.qlda.documentservice.exception.ErrorCode;
import com.qlda.documentservice.repository.TepDinhKemRepository;
import com.qlda.documentservice.repository.VanBanRepository;
import com.qlda.documentservice.service.InternalDocumentService;
import com.qlda.documentservice.specification.VanBanSpecification;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InternalDocumentServiceImpl implements InternalDocumentService {

    private final VanBanRepository vanBanRepository;
    private final TepDinhKemRepository tepDinhKemRepository;

    public InternalDocumentServiceImpl(VanBanRepository vanBanRepository, TepDinhKemRepository tepDinhKemRepository) {
        this.vanBanRepository = vanBanRepository;
        this.tepDinhKemRepository = tepDinhKemRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public InternalDocumentResponses.InternalDocumentResponse getInternalDocument(Long id) {
        VanBan vanBan = getDocumentOrThrow(id);
        return new InternalDocumentResponses.InternalDocumentResponse(
            vanBan.getId(),
            vanBan.getSoKyHieu(),
            vanBan.getTrichYeu(),
            vanBan.getLoaiVanBan() == null ? null : vanBan.getLoaiVanBan().getId(),
            vanBan.getLoaiVanBan() == null ? null : vanBan.getLoaiVanBan().getTenLoaiVanBan(),
            mapDocumentType(vanBan.getPhanLoaiVanBan()),
            vanBan.getDonViChuTriId(),
            vanBan.getNguoiTaoId(),
            vanBan.getHanXuLy(),
            vanBan.getTrangThai(),
            vanBan.getDaOCR(),
            vanBan.getDaKySo()
        );
    }

    @Override
    @Transactional(readOnly = true)
    public InternalDocumentResponses.InternalDocumentContentResponse getDocumentContent(Long id) {
        VanBan vanBan = getDocumentOrThrow(id);
        String ocrText = vanBan.getNoiDungOCR();
        String content = (ocrText != null && !ocrText.isBlank()) ? ocrText : vanBan.getTrichYeu();
        return new InternalDocumentResponses.InternalDocumentContentResponse(
            vanBan.getId(),
            vanBan.getTrichYeu(),
            content,
            ocrText,
            "vi"
        );
    }

    @Override
    @Transactional(readOnly = true)
    public List<InternalDocumentResponses.InternalAttachmentResponse> getDocumentAttachments(Long id) {
        getDocumentOrThrow(id);
        return tepDinhKemRepository.findByVanBan_Id(id).stream()
            .map(this::mapAttachment)
            .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<InternalDocumentResponses.SearchDocumentResponse> searchDocuments(String keyword, int limit) {
        if (keyword == null || keyword.isBlank()) {
            return List.of();
        }
        int safeLimit = Math.max(1, Math.min(limit, 10));
        Specification<VanBan> spec = Specification.where(VanBanSpecification.daXoaFalse())
            .and(VanBanSpecification.keyword(keyword));
        return vanBanRepository.findAll(spec, PageRequest.of(0, safeLimit)).getContent().stream()
            .map(v -> new InternalDocumentResponses.SearchDocumentResponse(
                v.getId(),
                v.getSoKyHieu(),
                v.getTrichYeu(),
                v.getLoaiVanBan() == null ? null : v.getLoaiVanBan().getTenLoaiVanBan(),
                v.getPhanLoaiVanBan(),
                v.getTrangThai()
            ))
            .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<InternalDocumentResponses.SearchDocumentResponse> listDocumentsForAiIndex(int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 500));
        return vanBanRepository.findAll(PageRequest.of(0, safeLimit)).getContent().stream()
            .filter(v -> !Boolean.TRUE.equals(v.getDaXoa()))
            .map(v -> new InternalDocumentResponses.SearchDocumentResponse(
                v.getId(),
                v.getSoKyHieu(),
                v.getTrichYeu(),
                v.getLoaiVanBan() == null ? null : v.getLoaiVanBan().getTenLoaiVanBan(),
                v.getPhanLoaiVanBan(),
                v.getTrangThai()
            ))
            .toList();
    }

    @Override
    @Transactional
    public InternalDocumentResponses.UpdateStatusResponse updateDocumentStatus(Long id, InternalDocumentRequests.UpdateStatusRequest request) {
        VanBan vanBan = getDocumentOrThrow(id);
        vanBan.setTrangThai(request.trangThai());
        vanBan.setNgayCapNhat(LocalDateTime.now());
        vanBanRepository.save(vanBan);
        return new InternalDocumentResponses.UpdateStatusResponse(vanBan.getId(), vanBan.getTrangThai());
    }

    @Override
    @Transactional
    public InternalDocumentResponses.UpdateAssigneeResponse updateDocumentAssignee(Long id, InternalDocumentRequests.UpdateAssigneeRequest request) {
        VanBan vanBan = getDocumentOrThrow(id);
        if (request.donViXuLyId() != null) {
            vanBan.setDonViChuTriId(request.donViXuLyId());
        }
        if (request.hanXuLy() != null) {
            vanBan.setHanXuLy(request.hanXuLy());
        }
        vanBan.setNguoiXuLyId(request.nguoiXuLyId());
        vanBan.setNgayCapNhat(LocalDateTime.now());
        vanBanRepository.save(vanBan);
        return new InternalDocumentResponses.UpdateAssigneeResponse(vanBan.getId(), vanBan.getNguoiXuLyId(), vanBan.getDonViChuTriId());
    }

    @Override
    @Transactional
    public InternalDocumentResponses.UpdateWorkflowStatusResponse updateWorkflowStatus(
        Long id,
        InternalDocumentRequests.UpdateWorkflowStatusRequest request
    ) {
        VanBan vanBan = getDocumentOrThrow(id);
        vanBan.setTrangThaiQuyTrinh(request.workflowStatus());
        vanBan.setBuocHienTai(request.currentStep());
        vanBan.setXuLyVanBanId(request.processingId());
        vanBan.setNgayCapNhat(LocalDateTime.now());
        vanBanRepository.save(vanBan);
        return new InternalDocumentResponses.UpdateWorkflowStatusResponse(vanBan.getId(), request.workflowStatus(), request.processingId());
    }

    @Override
    @Transactional
    public InternalDocumentResponses.UpdateOcrStatusResponse updateOcrStatus(Long id, InternalDocumentRequests.UpdateOcrStatusRequest request) {
        VanBan vanBan = getDocumentOrThrow(id);
        vanBan.setDaOCR(request.daOCR());
        vanBan.setNgayCapNhat(LocalDateTime.now());
        vanBanRepository.save(vanBan);
        return new InternalDocumentResponses.UpdateOcrStatusResponse(vanBan.getId(), vanBan.getDaOCR());
    }

    @Override
    @Transactional
    public InternalDocumentResponses.UpdateOcrStatusResponse updateOcrContent(
        Long id, InternalDocumentRequests.UpdateOcrContentRequest request
    ) {
        VanBan vanBan = getDocumentOrThrow(id);
        String text = request.ocrText() == null ? "" : request.ocrText().trim();
        vanBan.setNoiDungOCR(text);
        vanBan.setDaOCR(!text.isBlank());
        vanBan.setNgayCapNhat(LocalDateTime.now());
        vanBanRepository.save(vanBan);
        return new InternalDocumentResponses.UpdateOcrStatusResponse(vanBan.getId(), vanBan.getDaOCR());
    }

    @Override
    @Transactional(readOnly = true)
    public InternalDocumentResponses.AccessCheckResponse checkDocumentAccess(InternalDocumentRequests.AccessCheckRequest request) {
        List<Long> requestedDocumentIds = request.documentIds().stream()
            .filter(Objects::nonNull)
            .toList();
        if (requestedDocumentIds.isEmpty()) {
            return new InternalDocumentResponses.AccessCheckResponse(List.of());
        }

        Set<Long> existingAccessibleIds = Set.copyOf(
            vanBanRepository.findAccessibleDocumentIds(request.userId(), requestedDocumentIds)
        );

        List<Long> allowedDocumentIds = requestedDocumentIds.stream()
            .filter(existingAccessibleIds::contains)
            .distinct()
            .toList();
        return new InternalDocumentResponses.AccessCheckResponse(allowedDocumentIds);
    }

    @Override
    @Transactional(readOnly = true)
    public InternalDocumentResponses.MyUploadedDocumentCountResponse getMyUploadedDocumentCount(Long userId) {
        long count = vanBanRepository.countByDaXoaFalseAndNguoiTaoId(userId);
        return new InternalDocumentResponses.MyUploadedDocumentCountResponse(userId, count);
    }

    @Override
    @Transactional(readOnly = true)
    public InternalDocumentResponses.TotalDocumentCountResponse getTotalDocumentCount() {
        long count = vanBanRepository.countByDaXoaFalse();
        return new InternalDocumentResponses.TotalDocumentCountResponse(count);
    }

    @Override
    @Transactional(readOnly = true)
    public InternalDocumentResponses.InternalDocumentStatisticsResponse getStatistics(
        LocalDate fromDate,
        LocalDate toDate,
        Integer donViId,
        String groupBy
    ) {
        List<VanBan> filtered = vanBanRepository.findAll().stream()
            .filter(v -> !Boolean.TRUE.equals(v.getDaXoa()))
            .filter(v -> donViId == null || donViId.equals(v.getDonViChuTriId()))
            .filter(v -> inDateRange(v, fromDate, toDate))
            .toList();

        long total = filtered.size();
        long incoming = filtered.stream().filter(v -> Integer.valueOf(DocumentConstants.PHAN_LOAI_VAN_BAN_DEN).equals(v.getPhanLoaiVanBan())).count();
        long outgoing = filtered.stream().filter(v -> Integer.valueOf(DocumentConstants.PHAN_LOAI_VAN_BAN_DI).equals(v.getPhanLoaiVanBan())).count();

        List<InternalDocumentResponses.StatisticItemResponse> items = mapStatisticItems(filtered, groupBy);
        return new InternalDocumentResponses.InternalDocumentStatisticsResponse(total, incoming, outgoing, items);
    }

    @Override
    @Transactional(readOnly = true)
    public InternalDocumentResponses.InternalOverdueDocumentsResponse getOverdueDocuments(Integer donViId, Long nguoiXuLyId, int page, int size) {
        LocalDateTime now = LocalDateTime.now();
        List<VanBan> overdue = vanBanRepository.findAll().stream()
            .filter(v -> !Boolean.TRUE.equals(v.getDaXoa()))
            .filter(v -> donViId == null || donViId.equals(v.getDonViChuTriId()))
            .filter(v -> nguoiXuLyId == null || nguoiXuLyId.equals(v.getNguoiXuLyId()))
            .filter(v -> v.getHanXuLy() != null && v.getHanXuLy().isBefore(now))
            .filter(v -> !Integer.valueOf(DocumentConstants.TRANG_THAI_DA_PHAT_HANH).equals(v.getTrangThai()))
            .sorted(Comparator.comparing(VanBan::getHanXuLy))
            .toList();

        int safePage = Math.max(page, 0);
        int safeSize = Math.max(size, 1);
        int fromIndex = Math.min(safePage * safeSize, overdue.size());
        int toIndex = Math.min(fromIndex + safeSize, overdue.size());
        List<InternalDocumentResponses.OverdueDocumentItemResponse> content = overdue.subList(fromIndex, toIndex).stream()
            .map(v -> mapOverdue(v, now))
            .toList();

        return new InternalDocumentResponses.InternalOverdueDocumentsResponse(content, safePage, safeSize, overdue.size());
    }

    private boolean inDateRange(VanBan vanBan, LocalDate fromDate, LocalDate toDate) {
        if (vanBan.getNgayTao() == null) {
            return true;
        }
        LocalDate value = vanBan.getNgayTao().toLocalDate();
        if (fromDate != null && value.isBefore(fromDate)) {
            return false;
        }
        return toDate == null || !value.isAfter(toDate);
    }

    private List<InternalDocumentResponses.StatisticItemResponse> mapStatisticItems(
        List<VanBan> filtered,
        String groupBy
    ) {
        String normalized = groupBy == null ? "status" : groupBy.trim().toLowerCase();
        return switch (normalized) {
            case "status" -> mapStatusStatistics(filtered);
            case "type" -> mapTypeStatistics(filtered);
            case "unit" -> mapUnitStatistics(filtered);
            case "month" -> mapMonthStatistics(filtered);
            default -> List.of();
        };
    }

    private List<InternalDocumentResponses.StatisticItemResponse> mapStatusStatistics(List<VanBan> documents) {
        Map<String, Long> counts = new LinkedHashMap<>();
        counts.put("PENDING", 0L);
        counts.put("PROCESSING", 0L);
        counts.put("COMPLETED", 0L);
        counts.put("REJECTED", 0L);

        for (VanBan document : documents) {
            String label = mapDashboardStatus(document.getTrangThai());
            counts.compute(label, (key, value) -> value == null ? 1L : value + 1L);
        }

        return counts.entrySet().stream()
            .map(entry -> new InternalDocumentResponses.StatisticItemResponse(entry.getKey(), entry.getValue()))
            .toList();
    }

    private String mapDashboardStatus(Integer status) {
        if (status == null || status == DocumentConstants.TRANG_THAI_NHAP) {
            return "PENDING";
        }
        if (status == DocumentConstants.TRANG_THAI_DANG_XU_LY
            || status == DocumentConstants.TRANG_THAI_DA_CHUYEN
            || status == DocumentConstants.TRANG_THAI_TRINH_KY) {
            return "PROCESSING";
        }
        if (status == DocumentConstants.TRANG_THAI_DA_KY
            || status == DocumentConstants.TRANG_THAI_DA_PHAT_HANH) {
            return "COMPLETED";
        }
        return "REJECTED";
    }

    private List<InternalDocumentResponses.StatisticItemResponse> mapTypeStatistics(List<VanBan> documents) {
        Map<String, Long> grouped = documents.stream()
            .collect(Collectors.groupingBy(
                document -> document.getLoaiVanBan() == null
                    ? "Chưa phân loại"
                    : document.getLoaiVanBan().getTenLoaiVanBan(),
                Collectors.counting()
            ));
        return toSortedStatisticItems(grouped);
    }

    private List<InternalDocumentResponses.StatisticItemResponse> mapUnitStatistics(List<VanBan> documents) {
        Map<String, Long> grouped = documents.stream()
            .collect(Collectors.groupingBy(
                document -> document.getDonViChuTriId() == null
                    ? "Chưa xác định"
                    : "Đơn vị " + document.getDonViChuTriId(),
                Collectors.counting()
            ));
        return toSortedStatisticItems(grouped);
    }

    private List<InternalDocumentResponses.StatisticItemResponse> mapMonthStatistics(List<VanBan> documents) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MM/yyyy");
        Map<YearMonth, Long> grouped = documents.stream()
            .filter(document -> document.getNgayTao() != null)
            .collect(Collectors.groupingBy(
                document -> YearMonth.from(document.getNgayTao()),
                Collectors.counting()
            ));
        return grouped.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .map(entry -> new InternalDocumentResponses.StatisticItemResponse(
                entry.getKey().format(formatter),
                entry.getValue()
            ))
            .toList();
    }

    private List<InternalDocumentResponses.StatisticItemResponse> toSortedStatisticItems(Map<String, Long> grouped) {
        return grouped.entrySet().stream()
            .sorted(Map.Entry.comparingByKey(String.CASE_INSENSITIVE_ORDER))
            .map(entry -> new InternalDocumentResponses.StatisticItemResponse(entry.getKey(), entry.getValue()))
            .toList();
    }

    private InternalDocumentResponses.InternalAttachmentResponse mapAttachment(TepDinhKem tepDinhKem) {
        return new InternalDocumentResponses.InternalAttachmentResponse(
            tepDinhKem.getId(),
            tepDinhKem.getTenTep(),
            tepDinhKem.getDuongDanTep(),
            tepDinhKem.getLoaiTep(),
            tepDinhKem.getKichThuoc()
        );
    }

    private InternalDocumentResponses.OverdueDocumentItemResponse mapOverdue(VanBan vanBan, LocalDateTime now) {
        long soNgayTre = Math.max(1, ChronoUnit.DAYS.between(vanBan.getHanXuLy().toLocalDate(), now.toLocalDate()));
        return new InternalDocumentResponses.OverdueDocumentItemResponse(
            vanBan.getId(),
            vanBan.getSoKyHieu(),
            vanBan.getTrichYeu(),
            vanBan.getHanXuLy(),
            soNgayTre,
            vanBan.getTrangThai()
        );
    }

    private String mapDocumentType(Integer phanLoaiVanBan) {
        if (Integer.valueOf(DocumentConstants.PHAN_LOAI_VAN_BAN_DEN).equals(phanLoaiVanBan)) {
            return "INCOMING";
        }
        if (Integer.valueOf(DocumentConstants.PHAN_LOAI_VAN_BAN_DI).equals(phanLoaiVanBan)) {
            return "OUTGOING";
        }
        if (Integer.valueOf(DocumentConstants.PHAN_LOAI_VAN_BAN_NHAP).equals(phanLoaiVanBan)) {
            return "DRAFT";
        }
        return "UNKNOWN";
    }

    private VanBan getDocumentOrThrow(Long id) {
        return vanBanRepository.findByIdAndDaXoaFalse(id)
            .orElseThrow(() -> BusinessException.notFound(ErrorCode.DOCUMENT_NOT_FOUND, "Document not found"));
    }
}
