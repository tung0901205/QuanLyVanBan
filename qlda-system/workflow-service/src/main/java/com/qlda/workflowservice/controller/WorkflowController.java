package com.qlda.workflowservice.controller;

import com.qlda.workflowservice.common.ApiResponse;
import com.qlda.workflowservice.dto.request.*;
import com.qlda.workflowservice.dto.response.*;
import com.qlda.workflowservice.service.WorkflowApiService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

@RestController
@RequestMapping("/api/workflows")
@RequiredArgsConstructor
public class WorkflowController {

    private final WorkflowApiService workflowApiService;

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','LANH_DAO','CHUYEN_VIEN')")
    public ApiResponse<WorkflowSummaryResponse> createWorkflow(@Valid @RequestBody WorkflowCreateRequest request) {
        return ApiResponse.ok("Create workflow successfully", workflowApiService.createWorkflow(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','LANH_DAO','CHUYEN_VIEN')")
    public ApiResponse<IdResponse> updateWorkflow(@PathVariable Integer id, @Valid @RequestBody WorkflowUpdateRequest request) {
        return ApiResponse.ok("Update workflow successfully", workflowApiService.updateWorkflow(id, request));
    }

    @GetMapping
    public ApiResponse<?> getWorkflows(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer loaiVanBanId,
            @RequestParam(required = false) Boolean suDung,
            Pageable pageable
    ) {
        return ApiResponse.ok("Get workflows successfully", workflowApiService.getWorkflows(keyword, loaiVanBanId, suDung, pageable));
    }

    @GetMapping("/{id}")
    public ApiResponse<WorkflowDetailResponse> getWorkflowDetail(@PathVariable Integer id) {
        return ApiResponse.ok("Get workflow detail successfully", workflowApiService.getWorkflowDetail(id));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','LANH_DAO','CHUYEN_VIEN')")
    public ApiResponse<IdResponse> deleteWorkflow(@PathVariable Integer id) {
        return ApiResponse.ok("Delete workflow successfully", workflowApiService.deleteWorkflow(id));
    }

    @PostMapping("/{workflowId}/steps")
    @PreAuthorize("hasAnyRole('ADMIN','LANH_DAO','CHUYEN_VIEN')")
    public ApiResponse<WorkflowStepResponse> createStep(
            @PathVariable Integer workflowId,
            @Valid @RequestBody WorkflowStepCreateRequest request
    ) {
        return ApiResponse.ok("Create workflow step successfully", workflowApiService.createWorkflowStep(workflowId, request));
    }

    @PutMapping("/{workflowId}/steps/{stepId}")
    @PreAuthorize("hasAnyRole('ADMIN','LANH_DAO','CHUYEN_VIEN')")
    public ApiResponse<IdResponse> updateStep(
            @PathVariable Integer workflowId,
            @PathVariable Long stepId,
            @Valid @RequestBody WorkflowStepUpdateRequest request
    ) {
        return ApiResponse.ok("Update workflow step successfully", workflowApiService.updateWorkflowStep(workflowId, stepId, request));
    }

    @DeleteMapping("/{workflowId}/steps/{stepId}")
    @PreAuthorize("hasAnyRole('ADMIN','LANH_DAO','CHUYEN_VIEN')")
    public ApiResponse<IdResponse> deleteStep(@PathVariable Integer workflowId, @PathVariable Long stepId) {
        return ApiResponse.ok("Delete workflow step successfully", workflowApiService.deleteWorkflowStep(workflowId, stepId));
    }

    @PatchMapping("/{workflowId}/steps/{stepId}/sla")
    @PreAuthorize("hasAnyRole('ADMIN','LANH_DAO','CHUYEN_VIEN')")
    public ApiResponse<SlaResponse> updateStepSla(
            @PathVariable Integer workflowId,
            @PathVariable Long stepId,
            @Valid @RequestBody SlaUpdateRequest request
    ) {
        return ApiResponse.ok("Update workflow step SLA successfully", workflowApiService.updateStepSla(workflowId, stepId, request));
    }

    @GetMapping("/approvals/pending")
    @PreAuthorize("hasAnyRole('ADMIN','LANH_DAO','CHUYEN_VIEN')")
    public ApiResponse<?> getPendingApprovals(
            @RequestParam(required = false) Long nguoiDuyetId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            Pageable pageable,
            @AuthenticationPrincipal Jwt jwt
    ) {
        if (isAdmin(jwt)) {
            return ApiResponse.ok("Get pending approvals successfully",
                    workflowApiService.getPendingApprovals(nguoiDuyetId, keyword, fromDate, toDate, pageable));
        }
        return ApiResponse.ok("Get pending approvals successfully",
                workflowApiService.getPendingApprovalsForUser(currentUserId(jwt), keyword, fromDate, toDate, pageable));
    }

    @PostMapping("/approvals/{processingId}/comment")
    @PreAuthorize("hasAnyRole('ADMIN','LANH_DAO','CHUYEN_VIEN')")
    public ApiResponse<ApprovalActionResponse> commentApproval(
            @PathVariable Long processingId,
            @Valid @RequestBody ApprovalCommentRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        ensureCanReview(processingId, jwt);
        return ApiResponse.ok("Comment approval successfully", workflowApiService.commentApproval(processingId, request));
    }

    @PostMapping("/approvals/{processingId}/approve")
    @PreAuthorize("hasAnyRole('ADMIN','LANH_DAO','CHUYEN_VIEN')")
    public ApiResponse<ApprovalActionResponse> approveDocument(
            @PathVariable Long processingId,
            @Valid @RequestBody ApprovalApproveRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        ensureCanReview(processingId, jwt);
        return ApiResponse.ok("Approve document successfully", workflowApiService.approveDocument(processingId, request));
    }

    @PostMapping("/approvals/{processingId}/reject")
    @PreAuthorize("hasAnyRole('ADMIN','LANH_DAO','CHUYEN_VIEN')")
    public ApiResponse<ApprovalActionResponse> rejectDocument(
            @PathVariable Long processingId,
            @Valid @RequestBody ApprovalRejectRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        ensureCanReview(processingId, jwt);
        return ApiResponse.ok("Reject document successfully", workflowApiService.rejectDocument(processingId, request));
    }

    @PostMapping("/delegations")
    @PreAuthorize("hasAnyRole('ADMIN','LANH_DAO','CHUYEN_VIEN')")
    public ApiResponse<DelegationResponse> createDelegation(
            @Valid @RequestBody DelegationCreateRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        if (!isAdmin(jwt) && !Objects.equals(request.nguoiUyQuyenId(), currentUserId(jwt))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Cannot create delegation for another user");
        }
        return ApiResponse.ok("Create delegation successfully", workflowApiService.createDelegation(request));
    }

    @GetMapping("/delegations")
    @PreAuthorize("hasAnyRole('ADMIN','LANH_DAO','CHUYEN_VIEN')")
    public ApiResponse<?> getDelegations(
            @RequestParam(required = false) Long nguoiUyQuyenId,
            @RequestParam(required = false) Long nguoiDuocUyQuyenId,
            @RequestParam(required = false) Boolean active,
            Pageable pageable,
            @AuthenticationPrincipal Jwt jwt
    ) {
        if (!isAdmin(jwt)) {
            Long me = currentUserId(jwt);
            boolean ownOutgoing = nguoiUyQuyenId != null && Objects.equals(nguoiUyQuyenId, me);
            boolean ownIncoming = nguoiDuocUyQuyenId != null && Objects.equals(nguoiDuocUyQuyenId, me);
            if (!ownOutgoing && !ownIncoming) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Chỉ được xem ủy quyền của chính mình");
            }
        }
        return ApiResponse.ok("Get delegations successfully",
                workflowApiService.getDelegations(nguoiUyQuyenId, nguoiDuocUyQuyenId, active, pageable));
    }

    @DeleteMapping("/delegations/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','LANH_DAO','CHUYEN_VIEN')")
    public ApiResponse<IdResponse> cancelDelegation(@PathVariable Long id) {
        return ApiResponse.ok("Cancel delegation successfully", workflowApiService.cancelDelegation(id));
    }

    @GetMapping("/documents/{documentId}/status")
    public ApiResponse<DocumentStatusResponse> getDocumentStatus(@PathVariable Long documentId) {
        return ApiResponse.ok("Get document workflow status successfully", workflowApiService.getDocumentStatus(documentId));
    }

    @GetMapping("/documents/{documentId}/timeline")
    public ApiResponse<List<TimelineItemResponse>> getDocumentTimeline(@PathVariable Long documentId) {
        return ApiResponse.ok("Get document timeline successfully", workflowApiService.getDocumentTimeline(documentId));
    }

    @GetMapping("/processings/{processingId}")
    public ApiResponse<ProcessingDetailResponse> getProcessingDetail(@PathVariable Long processingId) {
        return ApiResponse.ok("Get processing detail successfully", workflowApiService.getProcessingDetail(processingId));
    }

    @PostMapping("/reminders/check-deadlines")
    public ApiResponse<DeadlineCheckResponse> checkDeadlines(@Valid @RequestBody ReminderCheckDeadlineRequest request) {
        return ApiResponse.ok("Check deadlines successfully", workflowApiService.checkDeadlines(request));
    }

    @PostMapping("/reminders/send")
    public ApiResponse<ReminderSendResponse> sendReminders(@Valid @RequestBody ReminderSendRequest request) {
        return ApiResponse.ok("Send reminders successfully", workflowApiService.sendReminders(request));
    }

    @PostMapping("/documents/{documentId}/transfer")
    @PreAuthorize("hasAnyRole('ADMIN','CHUYEN_VIEN','LANH_DAO')")
    public ApiResponse<TransferResponse> transferDocument(
            @PathVariable Long documentId,
            @Valid @RequestBody TransferDocumentRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        Long senderId = isAdmin(jwt) ? request.nguoiGuiId() : currentUserId(jwt);
        TransferDocumentRequest securedRequest = new TransferDocumentRequest(
                senderId,
                request.nguoiNhanId(),
                request.donViXuLyId(),
                request.buocQuyTrinhId(),
                request.hanhDongXuLy(),
                request.yKienXuLy(),
                request.hanXuLy()
        );
        return ApiResponse.ok("Transfer document successfully",
                workflowApiService.transferDocument(documentId, securedRequest));
    }

    @PostMapping("/processings/{processingId}/receive")
    @PreAuthorize("hasAnyRole('ADMIN','CHUYEN_VIEN','LANH_DAO')")
    public ApiResponse<ReceiveResponse> receiveDocument(
            @PathVariable Long processingId,
            @Valid @RequestBody ReceiveProcessingRequest request
    ) {
        return ApiResponse.ok("Receive document successfully", workflowApiService.receiveDocument(processingId, request));
    }

    @PostMapping("/processings/{processingId}/complete")
    @PreAuthorize("hasAnyRole('ADMIN','CHUYEN_VIEN','LANH_DAO')")
    public ApiResponse<CompleteResponse> completeProcessing(
            @PathVariable Long processingId,
            @Valid @RequestBody CompleteProcessingRequest request
    ) {
        return ApiResponse.ok("Complete processing successfully", workflowApiService.completeProcessing(processingId, request));
    }

    @GetMapping("/sla")
    @PreAuthorize("hasAnyRole('ADMIN','LANH_DAO','CHUYEN_VIEN')")
    public ApiResponse<List<SlaResponse>> getSlaList(@RequestParam(required = false) Integer workflowId) {
        return ApiResponse.ok("Get SLA list successfully", workflowApiService.getSlaList(workflowId));
    }

    @GetMapping("/sla/violations")
    @PreAuthorize("hasAnyRole('ADMIN','LANH_DAO','CHUYEN_VIEN')")
    public ApiResponse<List<SlaViolationResponse>> getSlaViolations(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(required = false) Integer donViId
    ) {
        return ApiResponse.ok("Get SLA violations successfully", workflowApiService.getSlaViolations(fromDate, toDate, donViId));
    }
    private void ensureCanReview(Long processingId, Jwt jwt) {
        if (isAdmin(jwt)) return;
        if (!workflowApiService.canReviewProcessing(processingId, currentUserId(jwt))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bạn không có quyền phê duyệt văn bản này hoặc ủy quyền đã hết hiệu lực");
        }
    }

    private Long currentUserId(Jwt jwt) {
        if (jwt == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing authenticated user");
        }
        Object raw = jwt.getClaims().get("uid");
        if (raw instanceof Number number) return number.longValue();
        try {
            return Long.valueOf(String.valueOf(raw));
        } catch (RuntimeException ex) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid user identifier");
        }
    }

    private boolean isAdmin(Jwt jwt) {
        if (jwt == null) return false;
        Object raw = jwt.getClaims().get("roles");
        if (raw instanceof Collection<?> values) {
            return values.stream().filter(Objects::nonNull)
                    .map(String::valueOf)
                    .map(value -> value.replace("ROLE_", "").toUpperCase())
                    .anyMatch("ADMIN"::equals);
        }
        return raw != null && String.valueOf(raw).toUpperCase().contains("ADMIN");
    }

}
