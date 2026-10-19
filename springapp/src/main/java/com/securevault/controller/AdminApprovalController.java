package com.securevault.controller;

import com.securevault.dto.ApprovalDTO;
import com.securevault.dto.ApprovalDecisionRequest;
import com.securevault.dto.PageResponse;
import com.securevault.entity.ApprovalStatus;
import com.securevault.exception.BadRequestException;
import com.securevault.service.ApprovalService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Collections;
import java.util.Map;
import javax.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Admin Approvals", description = "Admin-only endpoints for managing document approvals and rejection workflows")
@RestController
@RequestMapping("/api/admin/approvals")
@PreAuthorize("hasRole('ADMIN')")
public class AdminApprovalController {

    private final ApprovalService approvalService;

    public AdminApprovalController(ApprovalService approvalService) {
        this.approvalService = approvalService;
    }

    /**
     * GET /api/admin/approvals?status=PENDING
     */
    @Operation(summary = "List approval requests for Admin", description = "Retrieves paginated approvals filtered by status (PENDING, APPROVED, REJECTED, ALL)")
    @GetMapping
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
     * GET /api/admin/approvals/pending-count
     */
    @Operation(summary = "Get count of pending approvals", description = "Returns total number of pending approval requests for admin badge")
    @GetMapping("/pending-count")
    public ResponseEntity<Map<String, Long>> getPendingCount() {
        long count = approvalService.getPendingCount();
        return ResponseEntity.ok(Map.of("count", count, "pending", count));
    }

    /**
     * GET /api/admin/approvals/{id}
     */
    @Operation(summary = "Get approval details for Admin", description = "Retrieves details of a specific document approval request")
    @GetMapping("/{id:[0-9]+}")
    public ResponseEntity<ApprovalDTO> getApprovalById(
            @PathVariable Long id,
            Authentication authentication) {
        String email = authentication != null ? authentication.getName() : null;
        ApprovalDTO dto = approvalService.getApprovalById(id, email, true);
        return ResponseEntity.ok(dto);
    }

    /**
     * POST /api/admin/approvals/{id}/approve {comments?}
     */
    @Operation(summary = "Approve document", description = "Admin approves a pending document request and triggers QR code generation")
    @PostMapping("/{id:[0-9]+}/approve")
    public ResponseEntity<ApprovalDTO> approve(
            @PathVariable Long id,
            @Valid @RequestBody(required = false) ApprovalDecisionRequest request,
            Authentication authentication) {
        String email = authentication != null ? authentication.getName() : null;
        String comments = request != null ? request.getComments() : null;

        ApprovalDTO dto = approvalService.approveApproval(id, comments, email, true);
        return ResponseEntity.ok(dto);
    }

    /**
     * POST /api/admin/approvals/{id}/reject {comments}
     */
    @Operation(summary = "Reject document", description = "Admin rejects a pending document request with mandatory reviewer comments")
    @PostMapping("/{id:[0-9]+}/reject")
    public ResponseEntity<ApprovalDTO> reject(
            @PathVariable Long id,
            @Valid @RequestBody(required = false) ApprovalDecisionRequest request,
            Authentication authentication) {
        String email = authentication != null ? authentication.getName() : null;
        String comments = request != null ? request.getComments() : null;

        ApprovalDTO dto = approvalService.rejectApproval(id, comments, email, true);
        return ResponseEntity.ok(dto);
    }
}
