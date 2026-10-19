package com.securevault.controller;

import com.securevault.dto.AnalyticsSummaryDTO;
import com.securevault.dto.AuditLogDTO;
import com.securevault.dto.CategoryCountDTO;
import com.securevault.dto.MonthlyApprovalDTO;
import com.securevault.dto.MonthlyCountDTO;
import com.securevault.exception.ForbiddenException;
import com.securevault.security.CustomUserDetailsService;
import com.securevault.security.JwtAuthenticationFilter;
import com.securevault.security.JwtUtil;
import com.securevault.service.AnalyticsService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AnalyticsController.class)
@AutoConfigureMockMvc(addFilters = false) // Unit test endpoints with security context mocked
public class AnalyticsControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AnalyticsService analyticsService;

    @MockBean
    private JwtUtil jwtUtil;

    @MockBean
    private CustomUserDetailsService customUserDetailsService;

    @MockBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @Test
    @WithMockUser(username = "admin@securevault.com", roles = {"ADMIN"})
    @DisplayName("GET /api/analytics/summary as ADMIN returns 200 with metrics")
    void testGetSummary_Admin() throws Exception {
        AnalyticsSummaryDTO summary = new AnalyticsSummaryDTO(
                120L, 10L, 95L, 15L, 60L, 35L, 204800L
        );
        when(analyticsService.getSummary(eq("admin@securevault.com"), any())).thenReturn(summary);

        mockMvc.perform(get("/api/analytics/summary")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalDocuments").value(120))
                .andExpect(jsonPath("$.pendingApprovals").value(10))
                .andExpect(jsonPath("$.approvedDocuments").value(95))
                .andExpect(jsonPath("$.rejectedDocuments").value(15))
                .andExpect(jsonPath("$.verifiedDocuments").value(60))
                .andExpect(jsonPath("$.activeUsers").value(35))
                .andExpect(jsonPath("$.storageUsedBytes").value(204800));
    }

    @Test
    @WithMockUser(username = "verifier@securevault.com", roles = {"VERIFIER"})
    @DisplayName("GET /api/analytics/summary as VERIFIER throws 403 Forbidden")
    void testGetSummary_VerifierForbidden() throws Exception {
        when(analyticsService.getSummary(eq("verifier@securevault.com"), any()))
                .thenThrow(new ForbiddenException("Verifiers do not have permission to access analytics dashboard"));

        mockMvc.perform(get("/api/analytics/summary")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "admin@securevault.com", roles = {"ADMIN"})
    @DisplayName("GET /api/analytics/uploads-by-month returns 200 and 12-month array")
    void testGetUploadsByMonth() throws Exception {
        List<MonthlyCountDTO> months = List.of(
                new MonthlyCountDTO("2026-02", "Feb 2026", 12L),
                new MonthlyCountDTO("2026-03", "Mar 2026", 18L)
        );
        when(analyticsService.getUploadsByMonth(eq("admin@securevault.com"), any())).thenReturn(months);

        mockMvc.perform(get("/api/analytics/uploads-by-month")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].month").value("2026-02"))
                .andExpect(jsonPath("$[0].count").value(12));
    }

    @Test
    @WithMockUser(username = "admin@securevault.com", roles = {"ADMIN"})
    @DisplayName("GET /api/analytics/approvals-by-month returns 200 with approved/rejected counts")
    void testGetApprovalsByMonth() throws Exception {
        List<MonthlyApprovalDTO> approvals = List.of(
                new MonthlyApprovalDTO("2026-02", "Feb 2026", 8L, 2L),
                new MonthlyApprovalDTO("2026-03", "Mar 2026", 15L, 3L)
        );
        when(analyticsService.getApprovalsByMonth(eq("admin@securevault.com"), any())).thenReturn(approvals);

        mockMvc.perform(get("/api/analytics/approvals-by-month")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[1].approved").value(15))
                .andExpect(jsonPath("$[1].rejected").value(3));
    }

    @Test
    @WithMockUser(username = "admin@securevault.com", roles = {"ADMIN"})
    @DisplayName("GET /api/analytics/documents-by-category returns 200 and category list")
    void testGetDocumentsByCategory() throws Exception {
        List<CategoryCountDTO> categories = List.of(
                new CategoryCountDTO("LEGAL", 40L),
                new CategoryCountDTO("FINANCIAL", 25L)
        );
        when(analyticsService.getDocumentsByCategory(eq("admin@securevault.com"), any())).thenReturn(categories);

        mockMvc.perform(get("/api/analytics/documents-by-category")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].category").value("LEGAL"))
                .andExpect(jsonPath("$[0].count").value(40));
    }

    @Test
    @WithMockUser(username = "admin@securevault.com", roles = {"ADMIN"})
    @DisplayName("GET /api/analytics/recent-activity returns 200 and audit log list")
    void testGetRecentActivity() throws Exception {
        AuditLogDTO log = new AuditLogDTO(
                1L, 10L, "Alice", "alice@example.com", "UPLOAD", "DOCUMENT", 100L,
                "127.0.0.1", "Uploaded file", LocalDateTime.now()
        );
        when(analyticsService.getRecentActivity(eq("admin@securevault.com"), any())).thenReturn(List.of(log));

        mockMvc.perform(get("/api/analytics/recent-activity")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].action").value("UPLOAD"))
                .andExpect(jsonPath("$[0].userName").value("Alice"));
    }
}
