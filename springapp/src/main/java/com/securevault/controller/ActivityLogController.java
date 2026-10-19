package com.securevault.controller;

import com.securevault.dto.ActivityLogDTO;
import com.securevault.service.ActivityLogService;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Activity Logs", description = "Legacy activity log endpoints (Admin only)")
@RestController
@RequestMapping({"/api/audit-logs", "/activity-logs"})
@PreAuthorize("hasRole('ADMIN')")
public class ActivityLogController {

    private final ActivityLogService activityLogService;

    public ActivityLogController(ActivityLogService activityLogService) {
        this.activityLogService = activityLogService;
    }

    @Operation(summary = "Get all activity logs", description = "Admin endpoint to retrieve legacy activity logs")
    @GetMapping({"", "/fetch"})
    public ResponseEntity<List<ActivityLogDTO>> getAllLogs() {
        return ResponseEntity.ok(activityLogService.getAllLogs());
    }

    @Operation(summary = "Create activity log", description = "Record a manual activity log entry")
    @PostMapping({"", "/create"})
    public ResponseEntity<ActivityLogDTO> createLog(@RequestBody ActivityLogDTO logDTO) {
        ActivityLogDTO savedLog = activityLogService.saveLog(logDTO);
        return ResponseEntity.ok(savedLog);
    }

    @Operation(summary = "Delete activity log", description = "Delete an activity log entry by ID")
    @DeleteMapping("/delete/{id}")
    public ResponseEntity<String> deleteLog(@PathVariable Long id) {
        activityLogService.deleteLog(id);
        return ResponseEntity.ok("Activity log deleted successfully!");
    }
}
