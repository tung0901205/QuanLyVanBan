package com.qlda.documentservice.controller;

import com.qlda.documentservice.common.ApiResponse;
import com.qlda.documentservice.common.PageResponse;
import com.qlda.documentservice.dto.request.DocumentRequests;
import com.qlda.documentservice.dto.response.DocumentResponses;
import com.qlda.documentservice.service.TemplateService;
import jakarta.validation.Valid;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/documents")
@PreAuthorize("hasAnyRole('ADMIN','CHUYEN_VIEN','LANH_DAO')")
public class TemplateController {
    private final TemplateService templateService;

    public TemplateController(TemplateService templateService) {
        this.templateService = templateService;
    }

    @PostMapping("/templates")
    @PreAuthorize("hasAnyRole('ADMIN','LANH_DAO','CHUYEN_VIEN')")
    public ApiResponse<DocumentResponses.TemplateSimpleResponse> create(@Valid @RequestBody DocumentRequests.TemplateCreateRequest request) {
        return ApiResponse.success("Create template successfully", templateService.create(request));
    }

    @PutMapping("/templates/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','LANH_DAO','CHUYEN_VIEN')")
    public ApiResponse<DocumentResponses.IdResponse> update(
        @PathVariable Integer id,
        @Valid @RequestBody DocumentRequests.TemplateUpdateRequest request
    ) {
        return ApiResponse.success("Update template successfully", templateService.update(id, request));
    }

    @DeleteMapping("/templates/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','LANH_DAO','CHUYEN_VIEN')")
    public ApiResponse<DocumentResponses.IdResponse> delete(@PathVariable Integer id) {
        return ApiResponse.success("Delete template successfully", templateService.delete(id));
    }

    @GetMapping("/templates")
    public ApiResponse<PageResponse<DocumentResponses.TemplateListItemResponse>> list(
        @RequestParam(required = false) String keyword,
        @RequestParam(required = false) Integer loaiVanBanId,
        @RequestParam(required = false) Boolean suDung,
        @PageableDefault(page = 0, size = 10) Pageable pageable
    ) {
        return ApiResponse.success("Get templates successfully", templateService.list(keyword, loaiVanBanId, suDung, pageable));
    }

    @GetMapping("/templates/{id}")
    public ApiResponse<DocumentResponses.TemplateDetailResponse> detail(@PathVariable Integer id) {
        return ApiResponse.success("Get template detail successfully", templateService.detail(id));
    }

    @PostMapping(value = "/templates/{id}/file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN','LANH_DAO','CHUYEN_VIEN')")
    public ApiResponse<DocumentResponses.TemplateDetailResponse> uploadFile(
        @PathVariable Integer id,
        @RequestPart("file") MultipartFile file
    ) {
        return ApiResponse.success("Upload template file successfully", templateService.uploadFile(id, file));
    }

    @GetMapping("/templates/{id}/file")
    public ResponseEntity<Resource> downloadFile(@PathVariable Integer id) {
        Resource resource = templateService.getFile(id);
        String filename = resource.getFilename() == null ? "template" : resource.getFilename();
        MediaType mediaType = MediaTypeFactory.getMediaType(filename)
            .orElse(MediaType.APPLICATION_OCTET_STREAM);
        return ResponseEntity.ok()
            .contentType(mediaType)
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
            .body(resource);
    }

    @PostMapping("/templates/{templateId}/apply")
    @PreAuthorize("hasAnyRole('ADMIN','CHUYEN_VIEN','LANH_DAO')")
    public ApiResponse<DocumentResponses.ApplyTemplateResponse> apply(
        @PathVariable Integer templateId,
        @RequestBody DocumentRequests.ApplyTemplateRequest request
    ) {
        return ApiResponse.success("Apply template successfully", templateService.apply(templateId, request));
    }

    @PostMapping("/from-template")
    @PreAuthorize("hasAnyRole('ADMIN','CHUYEN_VIEN','LANH_DAO')")
    public ApiResponse<DocumentResponses.CreateFromTemplateResponse> fromTemplate(
        @Valid @RequestBody DocumentRequests.CreateFromTemplateRequest request
    ) {
        return ApiResponse.success("Create document from template successfully", templateService.createFromTemplate(request));
    }
}
