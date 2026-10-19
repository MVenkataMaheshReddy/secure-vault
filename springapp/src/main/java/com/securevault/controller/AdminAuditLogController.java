package com.securevault.controller;

import com.securevault.dto.AuditLogDTO;
import com.securevault.service.AuditService;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Admin Audit Logs", description = "Endpoints for administrator audit trail querying and compliance export")
@RestController
@RequestMapping({"/api/admin/audit-logs", "/admin/audit-logs"})
@PreAuthorize("hasRole('ADMIN')")
public class AdminAuditLogController {

    private final AuditService auditService;

    public AdminAuditLogController(AuditService auditService) {
        this.auditService = auditService;
    }

    /**
     * GET /api/admin/audit-logs
     * Filterable and paginated audit logs for Admin inspection.
     */
    @Operation(summary = "Query audit logs", description = "Filterable, paginated query for security and compliance audit logs")
    @GetMapping
    public ResponseEntity<Page<AuditLogDTO>> getAuditLogs(
            @RequestParam(required = false) String user,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "timestamp") String sort,
            @RequestParam(defaultValue = "desc") String direction) {

        if (user != null && user.length() > 255) {
            throw new com.securevault.exception.BadRequestException("User filter cannot exceed 255 characters");
        }
        if (action != null && action.length() > 100) {
            throw new com.securevault.exception.BadRequestException("Action filter cannot exceed 100 characters");
        }
        if (entityType != null && entityType.length() > 100) {
            throw new com.securevault.exception.BadRequestException("EntityType filter cannot exceed 100 characters");
        }

        int safePage = Math.max(0, page);
        int safeSize = Math.min(Math.max(1, size), 100);

        LocalDateTime fromDateTime = from != null ? from.atStartOfDay() : null;
        LocalDateTime toDateTime = to != null ? to.atTime(LocalTime.MAX) : null;

        Sort sortObj = direction.equalsIgnoreCase("desc")
                ? Sort.by(sort).descending()
                : Sort.by(sort).ascending();
        Pageable pageable = PageRequest.of(safePage, safeSize, sortObj);

        Page<AuditLogDTO> result = auditService.getAuditLogs(user, action, entityType, fromDateTime, toDateTime, pageable);
        return ResponseEntity.ok(result);
    }

    /**
     * GET /api/admin/audit-logs/export
     * Export filtered audit logs to CSV for compliance and auditing.
     */
    @Operation(summary = "Export audit logs to CSV", description = "Export filtered audit logs as downloadable CSV spreadsheet for external compliance inspection")
    @GetMapping("/export")
    public ResponseEntity<byte[]> exportAuditLogsCsv(
            @RequestParam(required = false) String user,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {

        LocalDateTime fromDateTime = from != null ? from.atStartOfDay() : null;
        LocalDateTime toDateTime = to != null ? to.atTime(LocalTime.MAX) : null;

        byte[] csvData = auditService.exportAuditLogsToCsv(user, action, entityType, fromDateTime, toDateTime);

        String filename = "audit_logs_" + LocalDate.now() + ".csv";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(csvData);
    }
}
