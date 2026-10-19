package com.securevault.controller;

import com.securevault.dto.ApprovalDTO;
import com.securevault.dto.ApprovalDecisionRequest;
import com.securevault.dto.PageResponse;
import com.securevault.entity.ApprovalStatus;
import com.securevault.exception.BadRequestException;
import com.securevault.service.ApprovalService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "8. Approvals", description = "Endpoints for document review, approval, and rejection workflows")
@RestController
@RequestMapping("/api/approvals")
public class ApprovalController {

    private final ApprovalService approvalService;

    public ApprovalController(ApprovalService approvalService) {
        this.approvalService = approvalService;
    }

    private boolean isUserReviewerOrAdmin(Authentication authentication) {
        if (authentication == null) return false;
        return authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN") || a.getAuthority().equals("ROLE_REVIEWER"));
    }

    /**
     * GET /api/approvals?status=PENDING (REVIEWER/ADMIN; paginated)
     */
    @Operation(summary = "List approval requests", description = "Retrieves paginated approvals filtered by status (PENDING, APPROVED, REJECTED, ALL)")
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'REVIEWER')")
    public ResponseEntity<PageResponse<ApprovalDTO>> getApprovals(
            @RequestParam(value = "status", defaultValue = "PENDING") String status,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "10") int size) {

        int safePage = Math.max(0, page);
        int safeSize = Math.min(Math.max(1, size), 100);

        ApprovalStatus statusEnum = null;
        if (status != null && !status.isBlank() && !status.equalsIgnoreCase("ALL")) {
            try {
                statusEnum = ApprovalStatus.valueOf(status.trim().toUpperCase());
            } catch (IllegalArgumentException ex) {
                throw new BadRequestException("Invalid status: " + status + ". Allowed values: PENDING, APPROVED, REJECTED, ALL");
            }
        }

        PageResponse<ApprovalDTO> response = approvalService.getApprovals(statusEnum, safePage, safeSize);
        return ResponseEntity.ok(response);
    }

    /**
     * GET /api/approvals/{id}
     */
    @Operation(summary = "Get approval details", description = "Retrieves details of a specific document approval request")
    @GetMapping("/{id:[0-9]+}")
    @PreAuthorize("hasAnyRole('ADMIN', 'REVIEWER')")
    public ResponseEntity<ApprovalDTO> getApprovalById(
            @PathVariable Long id,
            Authentication authentication) {
        String email = authentication != null ? authentication.getName() : null;
        boolean isReviewerOrAdmin = isUserReviewerOrAdmin(authentication);
        ApprovalDTO dto = approvalService.getApprovalById(id, email, isReviewerOrAdmin);
        return ResponseEntity.ok(dto);
    }

    /**
     * POST /api/approvals/{id}/approve {comments?}
     */
    @Operation(summary = "Approve document", description = "Approves a pending document request and triggers QR code generation")
    @PostMapping("/{id:[0-9]+}/approve")
    @PreAuthorize("hasAnyRole('ADMIN', 'REVIEWER')")
    public ResponseEntity<ApprovalDTO> approve(
            @PathVariable Long id,
            @javax.validation.Valid @RequestBody(required = false) ApprovalDecisionRequest request,
            Authentication authentication) {
        String email = authentication != null ? authentication.getName() : null;
        boolean isReviewerOrAdmin = isUserReviewerOrAdmin(authentication);
        String comments = request != null ? request.getComments() : null;

        ApprovalDTO dto = approvalService.approveApproval(id, comments, email, isReviewerOrAdmin);
        return ResponseEntity.ok(dto);
    }

    /**
     * POST /api/approvals/{id}/reject {comments required}
     */
    @Operation(summary = "Reject document", description = "Rejects a pending document request with mandatory reviewer feedback")
    @PostMapping("/{id:[0-9]+}/reject")
    @PreAuthorize("hasAnyRole('ADMIN', 'REVIEWER')")
    public ResponseEntity<ApprovalDTO> reject(
            @PathVariable Long id,
            @javax.validation.Valid @RequestBody(required = false) ApprovalDecisionRequest request,
            Authentication authentication) {
        String email = authentication != null ? authentication.getName() : null;
        boolean isReviewerOrAdmin = isUserReviewerOrAdmin(authentication);
        String comments = request != null ? request.getComments() : null;

        ApprovalDTO dto = approvalService.rejectApproval(id, comments, email, isReviewerOrAdmin);
        return ResponseEntity.ok(dto);
    }
}
