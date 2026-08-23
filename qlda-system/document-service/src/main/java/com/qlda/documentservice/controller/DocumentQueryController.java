package com.qlda.documentservice.controller;

import com.qlda.documentservice.common.ApiResponse;
import com.qlda.documentservice.dto.response.DocumentResponses;
import com.qlda.documentservice.service.DocumentWorkflowService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Unified detail endpoint for incoming, outgoing and internal documents. */
@RestController
@RequestMapping("/api/documents")
@PreAuthorize("hasAnyRole('ADMIN','CHUYEN_VIEN','LANH_DAO')")
public class DocumentQueryController {

    private final DocumentWorkflowService documentWorkflowService;

    public DocumentQueryController(DocumentWorkflowService documentWorkflowService) {
        this.documentWorkflowService = documentWorkflowService;
    }

    @GetMapping("/{id}")
    public ApiResponse<DocumentResponses.DocumentDetailResponse> detail(@PathVariable Long id) {
        return ApiResponse.success("Get document detail successfully", documentWorkflowService.getDocumentDetail(id));
    }
}
