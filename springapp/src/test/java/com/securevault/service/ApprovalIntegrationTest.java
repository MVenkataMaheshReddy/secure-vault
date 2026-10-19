package com.securevault.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.securevault.controller.ApprovalController;
import com.securevault.controller.DocumentController;
import com.securevault.dto.ApprovalDTO;
import com.securevault.dto.ApprovalDecisionRequest;
import com.securevault.dto.DocumentDTO;
import com.securevault.dto.PageResponse;
import com.securevault.entity.ApprovalStatus;
import com.securevault.entity.DocumentStatus;
import com.securevault.exception.BadRequestException;
import com.securevault.exception.ConflictException;
import com.securevault.exception.ForbiddenException;
import com.securevault.repository.DocumentRepository;
import com.securevault.repository.RevokedTokenRepository;
import com.securevault.repository.UserRepository;
import com.securevault.security.CustomAccessDeniedHandler;
import com.securevault.security.CustomAuthenticationEntryPoint;
import com.securevault.security.CustomUserDetailsService;
import com.securevault.security.JwtAuthenticationFilter;
import com.securevault.security.JwtUtil;
import com.securevault.security.SecurityConfig;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = {ApprovalController.class, DocumentController.class})
@Import({SecurityConfig.class, CustomAuthenticationEntryPoint.class, CustomAccessDeniedHandler.class})
public class ApprovalIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ApprovalService approvalService;

    @MockBean
    private DocumentService documentService;

    @MockBean
    private VerificationService verificationService;

    @MockBean
    private UserRepository userRepository;

    @MockBean
    private DocumentRepository documentRepository;

    @MockBean
    private JwtUtil jwtUtil;

    @MockBean
    private CustomUserDetailsService userDetailsService;

    @MockBean
    private RevokedTokenRepository revokedTokenRepository;

    private ApprovalDTO sampleApprovalDTO() {
        return new ApprovalDTO(
                1L,
                10L,
                "Audit Report 2026",
                "FINANCIAL",
                "Alice",
                "alice@securevault.com",
                1,
                ApprovalStatus.PENDING,
                null,
                null,
                null,
                "Initial document submission",
                LocalDateTime.now(),
                LocalDateTime.now()
        );
    }

    @Test
    @WithMockUser(username = "bob_reviewer@securevault.com", roles = {"REVIEWER"})
    @DisplayName("GET /api/approvals?status=PENDING accessible by REVIEWER")
    void testGetApprovalsQueueReviewer() throws Exception {
        PageResponse<ApprovalDTO> pageResponse = new PageResponse<>(
                List.of(sampleApprovalDTO()),
                0,
                10,
                1L,
                1,
                true
        );

        when(approvalService.getApprovals(eq(ApprovalStatus.PENDING), eq(0), eq(10)))
                .thenReturn(pageResponse);

        mockMvc.perform(get("/api/approvals?status=PENDING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(1))
                .andExpect(jsonPath("$.content[0].documentTitle").value("Audit Report 2026"))
                .andExpect(jsonPath("$.content[0].ownerEmail").value("alice@securevault.com"))
                .andExpect(jsonPath("$.content[0].status").value("PENDING"));
    }

    @Test
    @WithMockUser(username = "employee@securevault.com", roles = {"EMPLOYEE"})
    @DisplayName("GET /api/approvals forbidden for EMPLOYEE without reviewer role")
    void testGetApprovalsQueueForbiddenForEmployee() throws Exception {
        mockMvc.perform(get("/api/approvals?status=PENDING"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "bob_reviewer@securevault.com", roles = {"REVIEWER"})
    @DisplayName("POST /api/approvals/{id}/approve approves document and returns updated DTO")
    void testApproveEndpointSuccess() throws Exception {
        ApprovalDTO approvedDto = sampleApprovalDTO();
        approvedDto.setStatus(ApprovalStatus.APPROVED);
        approvedDto.setReviewerName("Bob Reviewer");
        approvedDto.setComments("LGTM");

        when(approvalService.approveApproval(eq(1L), any(), eq("bob_reviewer@securevault.com"), eq(true)))
                .thenReturn(approvedDto);

        ApprovalDecisionRequest req = new ApprovalDecisionRequest("LGTM");

        mockMvc.perform(post("/api/approvals/1/approve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.reviewerName").value("Bob Reviewer"))
                .andExpect(jsonPath("$.comments").value("LGTM"));
    }

    @Test
    @WithMockUser(username = "bob_reviewer@securevault.com", roles = {"REVIEWER"})
    @DisplayName("POST /api/approvals/{id}/reject requires comments (400 Bad Request)")
    void testRejectRequiresComments() throws Exception {
        when(approvalService.rejectApproval(eq(1L), eq(null), eq("bob_reviewer@securevault.com"), eq(true)))
                .thenThrow(new BadRequestException("Comments are required when rejecting an approval request"));

        mockMvc.perform(post("/api/approvals/1/reject")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Comments are required when rejecting an approval request"));
    }

    @Test
    @WithMockUser(username = "alice@securevault.com", roles = {"REVIEWER"})
    @DisplayName("POST /api/approvals/{id}/approve blocks self-approval (403 Forbidden)")
    void testSelfApprovalBlockedEndpoint() throws Exception {
        when(approvalService.approveApproval(eq(1L), any(), eq("alice@securevault.com"), eq(true)))
                .thenThrow(new ForbiddenException("You cannot approve your own document"));

        mockMvc.perform(post("/api/approvals/1/approve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"comments\":\"Approved\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("You cannot approve your own document"));
    }

    @Test
    @WithMockUser(username = "bob_reviewer@securevault.com", roles = {"REVIEWER"})
    @DisplayName("POST /api/approvals/{id}/approve on already decided approval returns 409 Conflict")
    void testApproveNonPendingReturnsConflict() throws Exception {
        when(approvalService.approveApproval(eq(1L), any(), eq("bob_reviewer@securevault.com"), eq(true)))
                .thenThrow(new ConflictException("Only PENDING approval requests can be decided. Current status: APPROVED"));

        mockMvc.perform(post("/api/approvals/1/approve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"comments\":\"Approved\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    @WithMockUser(username = "alice@securevault.com", roles = {"EMPLOYEE"})
    @DisplayName("GET /api/documents/{id}/approvals returns document approval history")
    void testGetDocumentApprovalHistory() throws Exception {
        when(approvalService.getApprovalHistory(eq(10L), eq("alice@securevault.com"), eq(false)))
                .thenReturn(List.of(sampleApprovalDTO()));

        mockMvc.perform(get("/api/documents/10/approvals"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].documentId").value(10))
                .andExpect(jsonPath("$[0].status").value("PENDING"));
    }

    @Test
    @WithMockUser(username = "alice@securevault.com", roles = {"EMPLOYEE"})
    @DisplayName("POST /api/documents/{id}/resubmit transitions rejected document back to PENDING")
    void testResubmitDocumentEndpoint() throws Exception {
        DocumentDTO resubmittedDoc = new DocumentDTO();
        resubmittedDoc.setId(10L);
        resubmittedDoc.setTitle("Audit Report 2026");
        resubmittedDoc.setStatus(DocumentStatus.PENDING.name());

        when(approvalService.resubmitDocument(eq(10L), eq("alice@securevault.com"), eq(false)))
                .thenReturn(resubmittedDoc);

        mockMvc.perform(post("/api/documents/10/resubmit"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }
}
