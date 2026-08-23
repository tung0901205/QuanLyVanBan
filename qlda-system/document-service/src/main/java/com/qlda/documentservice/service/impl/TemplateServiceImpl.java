package com.qlda.documentservice.service.impl;

import com.qlda.documentservice.client.AiServiceClient;
import com.qlda.documentservice.client.dto.AiClientDtos;
import com.qlda.documentservice.common.DocumentConstants;
import com.qlda.documentservice.common.PageResponse;
import com.qlda.documentservice.dto.request.DocumentRequests;
import com.qlda.documentservice.dto.response.DocumentResponses;
import com.qlda.documentservice.entity.LoaiVanBan;
import com.qlda.documentservice.entity.TemplateVanBan;
import com.qlda.documentservice.entity.TepDinhKem;
import com.qlda.documentservice.entity.VanBan;
import com.qlda.documentservice.exception.BusinessException;
import com.qlda.documentservice.exception.ErrorCode;
import com.qlda.documentservice.mapper.DocumentMapper;
import com.qlda.documentservice.repository.LoaiVanBanRepository;
import com.qlda.documentservice.repository.TemplateVanBanRepository;
import com.qlda.documentservice.repository.TepDinhKemRepository;
import com.qlda.documentservice.repository.VanBanRepository;
import com.qlda.documentservice.security.SecurityUtils;
import com.qlda.documentservice.service.TemplateService;
import com.qlda.documentservice.service.FileStorageService;
import com.qlda.documentservice.specification.TemplateVanBanSpecification;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class TemplateServiceImpl implements TemplateService {
    private static final Logger log = LoggerFactory.getLogger(TemplateServiceImpl.class);
    private static final String SOURCE_SERVICE = "document-service";

    private final TemplateVanBanRepository templateVanBanRepository;
    private final LoaiVanBanRepository loaiVanBanRepository;
    private final VanBanRepository vanBanRepository;
    private final TepDinhKemRepository tepDinhKemRepository;
    private final DocumentMapper documentMapper;
    private final SecurityUtils securityUtils;
    private final AiServiceClient aiServiceClient;
    private final FileStorageService fileStorageService;

    public TemplateServiceImpl(
        TemplateVanBanRepository templateVanBanRepository,
        LoaiVanBanRepository loaiVanBanRepository,
        VanBanRepository vanBanRepository,
        TepDinhKemRepository tepDinhKemRepository,
        DocumentMapper documentMapper,
        SecurityUtils securityUtils,
        AiServiceClient aiServiceClient,
        FileStorageService fileStorageService
    ) {
        this.templateVanBanRepository = templateVanBanRepository;
        this.loaiVanBanRepository = loaiVanBanRepository;
        this.vanBanRepository = vanBanRepository;
        this.tepDinhKemRepository = tepDinhKemRepository;
        this.documentMapper = documentMapper;
        this.securityUtils = securityUtils;
        this.aiServiceClient = aiServiceClient;
        this.fileStorageService = fileStorageService;
    }

    @Override
    @Transactional
    public DocumentResponses.TemplateSimpleResponse create(DocumentRequests.TemplateCreateRequest request) {
        if (templateVanBanRepository.existsByMaTemplate(request.maTemplate())) {
            throw BusinessException.conflict(ErrorCode.INVALID_REQUEST, "MaTemplate already exists");
        }
        TemplateVanBan templateVanBan = new TemplateVanBan();
        templateVanBan.setMaTemplate(request.maTemplate());
        templateVanBan.setTenTemplate(request.tenTemplate());
        templateVanBan.setLoaiVanBan(findLoaiVanBan(request.loaiVanBanId()));
        templateVanBan.setNoiDungMau(request.noiDungMau());
        templateVanBan.setTepMau(request.tepMau());
        templateVanBan.setSuDung(request.suDung() == null ? true : request.suDung());
        securityUtils.getCurrentUserId().ifPresent(templateVanBan::setNguoiTaoId);
        templateVanBan = templateVanBanRepository.save(templateVanBan);
        return new DocumentResponses.TemplateSimpleResponse(templateVanBan.getId(), templateVanBan.getMaTemplate());
    }

    @Override
    @Transactional
    public DocumentResponses.IdResponse update(Integer id, DocumentRequests.TemplateUpdateRequest request) {
        TemplateVanBan templateVanBan = getTemplateOrThrow(id);
        templateVanBan.setTenTemplate(request.tenTemplate());
        templateVanBan.setLoaiVanBan(findLoaiVanBan(request.loaiVanBanId()));
        templateVanBan.setNoiDungMau(request.noiDungMau());
        templateVanBan.setTepMau(request.tepMau());
        if (request.suDung() != null) {
            templateVanBan.setSuDung(request.suDung());
        }
        templateVanBanRepository.save(templateVanBan);
        return new DocumentResponses.IdResponse(id);
    }

    @Override
    @Transactional
    public DocumentResponses.IdResponse delete(Integer id) {
        TemplateVanBan templateVanBan = getTemplateOrThrow(id);
        templateVanBan.setSuDung(false);
        templateVanBanRepository.save(templateVanBan);
        return new DocumentResponses.IdResponse(id);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<DocumentResponses.TemplateListItemResponse> list(String keyword, Integer loaiVanBanId, Boolean suDung, Pageable pageable) {
        Specification<TemplateVanBan> spec = Specification.where(TemplateVanBanSpecification.keyword(keyword))
            .and(TemplateVanBanSpecification.loaiVanBanId(loaiVanBanId))
            .and(TemplateVanBanSpecification.suDung(suDung));
        Page<TemplateVanBan> page = templateVanBanRepository.findAll(spec, pageable);
        return new PageResponse<>(
            page.getContent().stream().map(documentMapper::toTemplateListItemResponse).toList(),
            page.getNumber(),
            page.getSize(),
            page.getTotalElements(),
            page.getTotalPages()
        );
    }

    @Override
    @Transactional(readOnly = true)
    public DocumentResponses.TemplateDetailResponse detail(Integer id) {
        return documentMapper.toTemplateDetailResponse(getTemplateOrThrow(id));
    }

    @Override
    @Transactional
    public DocumentResponses.TemplateDetailResponse uploadFile(Integer id, MultipartFile file) {
        TemplateVanBan templateVanBan = getTemplateOrThrow(id);
        String newPath = fileStorageService.store(file);
        templateVanBan.setTepMau(newPath);
        templateVanBanRepository.save(templateVanBan);
        return documentMapper.toTemplateDetailResponse(templateVanBan);
    }

    @Override
    @Transactional(readOnly = true)
    public Resource getFile(Integer id) {
        TemplateVanBan templateVanBan = getTemplateOrThrow(id);
        if (templateVanBan.getTepMau() == null || templateVanBan.getTepMau().isBlank()) {
            throw BusinessException.notFound(ErrorCode.ATTACHMENT_NOT_FOUND, "Template file not found");
        }
        return fileStorageService.load(templateVanBan.getTepMau());
    }

    @Override
    @Transactional(readOnly = true)
    public DocumentResponses.ApplyTemplateResponse apply(Integer templateId, DocumentRequests.ApplyTemplateRequest request) {
        TemplateVanBan templateVanBan = getTemplateOrThrow(templateId);
        String replacedContent = replacePlaceholder(templateVanBan.getNoiDungMau(), request.replaceData());
        return new DocumentResponses.ApplyTemplateResponse(request.documentId(), templateId, replacedContent);
    }

    @Override
    @Transactional
    public DocumentResponses.CreateFromTemplateResponse createFromTemplate(DocumentRequests.CreateFromTemplateRequest request) {
        TemplateVanBan templateVanBan = getTemplateOrThrow(request.templateId());
        if (Boolean.FALSE.equals(templateVanBan.getSuDung())) {
            throw BusinessException.badRequest(ErrorCode.INVALID_REQUEST, "Template đã ngừng sử dụng");
        }

        Integer loaiVanBanId = request.loaiVanBanId() != null
            ? request.loaiVanBanId()
            : (templateVanBan.getLoaiVanBan() == null ? null : templateVanBan.getLoaiVanBan().getId());
        LocalDate documentDate = request.ngayVanBan() == null ? LocalDate.now() : request.ngayVanBan();
        LocalDateTime now = LocalDateTime.now();

        VanBan vanBan = new VanBan();
        vanBan.setSoKyHieu(trimToNull(request.soKyHieu()));
        vanBan.setTrichYeu(request.trichYeu().trim());
        vanBan.setLoaiVanBan(findLoaiVanBan(loaiVanBanId));
        vanBan.setNguoiKy(trimToNull(request.nguoiKy()));
        vanBan.setNgayVanBan(documentDate.atStartOfDay());
        vanBan.setDonViChuTriId(request.donViChuTriId());
        vanBan.setDoMat(trimToNull(request.doMat()));
        vanBan.setDoKhan(trimToNull(request.doKhan()));
        vanBan.setPhanLoaiVanBan(DocumentConstants.PHAN_LOAI_VAN_BAN_DI);
        // Văn bản tạo từ template là một văn bản đi đã được khởi tạo đầy đủ,
        // không phải bản nháp. Người dùng có thể trình duyệt ngay từ Văn bản đi.
        vanBan.setTrangThai(DocumentConstants.TRANG_THAI_DANG_XU_LY);
        vanBan.setDaXoa(false);
        vanBan.setDaOCR(false);
        vanBan.setDaKySo(false);
        vanBan.setNgayTao(now);
        vanBan.setNgayCapNhat(now);
        securityUtils.getCurrentUserId().ifPresent(vanBan::setNguoiTaoId);
        vanBan = vanBanRepository.save(vanBan);

        Map<String, String> replacements = buildTemplateReplacements(templateVanBan, request, documentDate);

        // Tạo bản Word hoàn chỉnh riêng cho văn bản mới và thay placeholder bằng
        // dữ liệu người dùng vừa nhập. Template gốc luôn được giữ nguyên.
        if (templateVanBan.getTepMau() != null && !templateVanBan.getTepMau().isBlank()) {
            String documentFilePath = fileStorageService.copyAndReplace(templateVanBan.getTepMau(), replacements);
            String normalized = templateVanBan.getTepMau().replace("\\", "/");
            String sourceName = normalized.contains("/")
                ? normalized.substring(normalized.lastIndexOf('/') + 1)
                : normalized;
            String extension = sourceName.contains(".") ? sourceName.substring(sourceName.lastIndexOf('.')) : ".docx";

            TepDinhKem attachment = new TepDinhKem();
            attachment.setVanBan(vanBan);
            String numberPart = vanBan.getSoKyHieu() == null ? String.valueOf(vanBan.getId()) : sanitizeFileName(vanBan.getSoKyHieu());
            attachment.setTenTep("Van-ban-tu-mau-" + templateVanBan.getMaTemplate() + "-" + numberPart + extension);
            attachment.setDuongDanTep(documentFilePath);
            attachment.setLoaiTep(resolveFileType(sourceName));
            try {
                attachment.setKichThuoc(fileStorageService.load(documentFilePath).contentLength());
            } catch (Exception ignored) {
                // Kích thước chỉ là metadata hiển thị, không làm hỏng luồng tạo văn bản nếu không đọc được.
            }
            attachment.setNgayTaiLen(now);
            securityUtils.getCurrentUserId().ifPresent(attachment::setNguoiTaiLenId);
            tepDinhKemRepository.save(attachment);
        }

        requestIndexDocument(vanBan.getId());
        return new DocumentResponses.CreateFromTemplateResponse(vanBan.getId(), templateVanBan.getId(), vanBan.getTrangThai());
    }

    private Map<String, String> buildTemplateReplacements(
        TemplateVanBan template,
        DocumentRequests.CreateFromTemplateRequest request,
        LocalDate documentDate
    ) {
        Map<String, String> data = new HashMap<>();
        if (request.replaceData() != null) {
            data.putAll(request.replaceData());
        }
        data.putIfAbsent("SO_KY_HIEU", valueOrBlank(request.soKyHieu()));
        data.putIfAbsent("TRICH_YEU", valueOrBlank(request.trichYeu()));
        data.putIfAbsent("NGUOI_KY", valueOrBlank(request.nguoiKy()));
        data.putIfAbsent("NGAY_VAN_BAN", documentDate.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")));
        data.putIfAbsent("NGAY", String.format("%02d", documentDate.getDayOfMonth()));
        data.putIfAbsent("THANG", String.format("%02d", documentDate.getMonthValue()));
        data.putIfAbsent("NAM", String.valueOf(documentDate.getYear()));
        data.putIfAbsent("TEN_TEMPLATE", template.getTenTemplate());
        return data;
    }

    private String valueOrBlank(String value) {
        return value == null ? "" : value.trim();
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private String sanitizeFileName(String value) {
        return value.replaceAll("[^a-zA-Z0-9._-]+", "-");
    }

    private String replacePlaceholder(String rawContent, Map<String, String> replaceData) {
        if (rawContent == null) {
            return "";
        }
        if (replaceData == null || replaceData.isEmpty()) {
            return rawContent;
        }
        String output = rawContent;
        for (Map.Entry<String, String> entry : replaceData.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue() == null ? "" : entry.getValue();
            output = output.replace("{{" + key + "}}", value);
            output = output.replace("${" + key + "}", value);
        }
        return output;
    }

    private String resolveFileType(String filename) {
        if (filename == null || !filename.contains(".")) {
            return null;
        }
        return filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
    }

    private TemplateVanBan getTemplateOrThrow(Integer id) {
        return templateVanBanRepository.findById(id)
            .orElseThrow(() -> BusinessException.notFound(ErrorCode.TEMPLATE_NOT_FOUND, "Template not found"));
    }

    private LoaiVanBan findLoaiVanBan(Integer id) {
        if (id == null) {
            return null;
        }
        return loaiVanBanRepository.findById(id)
            .orElseThrow(() -> BusinessException.notFound(ErrorCode.DOCUMENT_TYPE_NOT_FOUND, "Document type not found"));
    }

    private void requestIndexDocument(Long documentId) {
        try {
            aiServiceClient.indexDocument(documentId, new AiClientDtos.IndexDocumentRequest(SOURCE_SERVICE));
        } catch (Exception ex) {
            log.warn("Index document failed after create from template: documentId={}", documentId, ex);
        }
    }
}
