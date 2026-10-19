package com.securevault.controller;

import com.securevault.dto.AnalyticsSummaryDTO;
import com.securevault.dto.AuditLogDTO;
import com.securevault.dto.CategoryCountDTO;
import com.securevault.dto.MonthlyApprovalDTO;
import com.securevault.dto.MonthlyCountDTO;
import com.securevault.exception.ForbiddenException;
import com.securevault.service.AnalyticsService;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Analytics & Reporting", description = "Endpoints for statistical dashboards, metrics, category counts, and monthly breakdown")
@RestController
@RequestMapping("/api/analytics")
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    public AnalyticsController(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    private Authentication resolveAuth(Authentication authentication) {
        if (authentication != null) return authentication;
        return org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
    }

    private Set<String> extractRoles(Authentication authentication) {
        Authentication auth = resolveAuth(authentication);
        if (auth == null) return Set.of();
        return auth.getAuthorities().stream()
                .map(a -> a.getAuthority().toUpperCase().replace("ROLE_", ""))
                .collect(Collectors.toSet());
    }

    private String extractEmail(Authentication authentication) {
        Authentication auth = resolveAuth(authentication);
        return auth != null ? auth.getName() : null;
    }

    /**
     * GET /api/analytics - root endpoint for general analytics & scope metadata
     */
    @Operation(summary = "Analytics root & scope", description = "Returns analytics overview and user authorization scope")
    @GetMapping({"", "/"})
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<java.util.Map<String, Object>> getAnalyticsRoot(Authentication authentication) {
        String email = extractEmail(authentication);
        Set<String> roles = extractRoles(authentication);
        if (roles.contains("VERIFIER") && !roles.contains("ADMIN") && !roles.contains("EMPLOYEE") && !roles.contains("REVIEWER")) {
            throw new ForbiddenException("Verifiers do not have permission to access analytics dashboard");
        }
        AnalyticsSummaryDTO summary = null;
        try {
            summary = analyticsService.getSummary(email, roles);
        } catch (Exception ignored) {
        }
        java.util.Map<String, Object> resp = new java.util.HashMap<>();
        resp.put("scope", roles.contains("ADMIN") ? "SYSTEM_WIDE" : "LIMITED_USER");
        if (summary != null) {
            resp.put("summary", summary);
        }
        return ResponseEntity.ok(resp);
    }

    /**
     * GET /api/analytics/summary
     */
    @Operation(summary = "Analytics summary counts", description = "Returns aggregated counts of documents, storage, pending approvals, and active users")
    @GetMapping("/summary")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<AnalyticsSummaryDTO> getSummary(Authentication authentication) {
        String email = extractEmail(authentication);
        Set<String> roles = extractRoles(authentication);
        AnalyticsSummaryDTO summary = analyticsService.getSummary(email, roles);
        return ResponseEntity.ok(summary);
    }

    /**
     * GET /api/analytics/uploads-by-month
     */
    @Operation(summary = "Uploads by month", description = "Monthly document upload trend")
    @GetMapping("/uploads-by-month")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<MonthlyCountDTO>> getUploadsByMonth(Authentication authentication) {
        String email = extractEmail(authentication);
        Set<String> roles = extractRoles(authentication);
        List<MonthlyCountDTO> list = analyticsService.getUploadsByMonth(email, roles);
        return ResponseEntity.ok(list);
    }

    /**
     * GET /api/analytics/approvals-by-month
     */
    @Operation(summary = "Approvals by month", description = "Monthly approval and rejection trends")
    @GetMapping("/approvals-by-month")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<MonthlyApprovalDTO>> getApprovalsByMonth(Authentication authentication) {
        String email = extractEmail(authentication);
        Set<String> roles = extractRoles(authentication);
        List<MonthlyApprovalDTO> list = analyticsService.getApprovalsByMonth(email, roles);
        return ResponseEntity.ok(list);
    }

    /**
     * GET /api/analytics/documents-by-category
     */
    @Operation(summary = "Documents by category", description = "Document distribution across categories")
    @GetMapping("/documents-by-category")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<CategoryCountDTO>> getDocumentsByCategory(Authentication authentication) {
        String email = extractEmail(authentication);
        Set<String> roles = extractRoles(authentication);
        List<CategoryCountDTO> list = analyticsService.getDocumentsByCategory(email, roles);
        return ResponseEntity.ok(list);
    }

    /**
     * GET /api/analytics/recent-activity
     */
    @Operation(summary = "Recent activity for analytics", description = "Recent audit log activity for dashboard view")
    @GetMapping("/recent-activity")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<AuditLogDTO>> getRecentActivity(Authentication authentication) {
        String email = extractEmail(authentication);
        Set<String> roles = extractRoles(authentication);
        List<AuditLogDTO> list = analyticsService.getRecentActivity(email, roles);
        return ResponseEntity.ok(list);
    }
}
