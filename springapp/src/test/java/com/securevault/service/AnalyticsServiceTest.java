package com.securevault.service;

import com.securevault.dto.AnalyticsSummaryDTO;
import com.securevault.dto.CategoryCountDTO;
import com.securevault.dto.MonthlyApprovalDTO;
import com.securevault.dto.MonthlyCountDTO;
import com.securevault.dto.AuditLogDTO;
import com.securevault.entity.ApprovalStatus;
import com.securevault.entity.AuditLog;
import com.securevault.entity.DocumentStatus;
import com.securevault.entity.User;
import com.securevault.exception.ForbiddenException;
import com.securevault.repository.ApprovalRepository;
import com.securevault.repository.AuditLogRepository;
import com.securevault.repository.DocumentRepository;
import com.securevault.repository.DocumentVersionRepository;
import com.securevault.repository.UserRepository;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class AnalyticsServiceTest {

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private DocumentVersionRepository documentVersionRepository;

    @Mock
    private ApprovalRepository approvalRepository;

    @Mock
    private AuditLogRepository auditLogRepository;

    @Mock
    private UserRepository userRepository;

    private AnalyticsService analyticsService;

    private User employeeUser;
    private User reviewerUser;

    @BeforeEach
    void setUp() {
        analyticsService = new AnalyticsService(
                documentRepository,
                documentVersionRepository,
                approvalRepository,
                auditLogRepository,
                userRepository
        );

        employeeUser = new User();
        employeeUser.setId(10L);
        employeeUser.setEmail("emp@securevault.com");
        employeeUser.setName("John Employee");

        reviewerUser = new User();
        reviewerUser.setId(20L);
        reviewerUser.setEmail("rev@securevault.com");
        reviewerUser.setName("Sarah Reviewer");
    }

    @Test
    @DisplayName("Admin Summary: returns system-wide metrics")
    void testGetSummary_AdminRole() {
        when(documentRepository.countByDeletedFalse()).thenReturn(100L);
        when(documentRepository.countByStatusAndDeletedFalse(DocumentStatus.PENDING)).thenReturn(15L);
        when(documentRepository.countByStatusAndDeletedFalse(DocumentStatus.APPROVED)).thenReturn(75L);
        when(documentRepository.countByStatusAndDeletedFalse(DocumentStatus.REJECTED)).thenReturn(10L);
        when(documentRepository.countVerifiedDocuments()).thenReturn(50L);
        when(auditLogRepository.countDistinctActiveUsers(any(LocalDateTime.class))).thenReturn(25L);
        when(documentVersionRepository.sumStorageUsedBytes()).thenReturn(104857600L); // 100 MB

        AnalyticsSummaryDTO summary = analyticsService.getSummary("admin@securevault.com", Set.of("ADMIN"));

        assertNotNull(summary);
        assertEquals(100L, summary.getTotalDocuments());
        assertEquals(15L, summary.getPendingApprovals());
        assertEquals(75L, summary.getApprovedDocuments());
        assertEquals(10L, summary.getRejectedDocuments());
        assertEquals(50L, summary.getVerifiedDocuments());
        assertEquals(25L, summary.getActiveUsers());
        assertEquals(104857600L, summary.getStorageUsedBytes());
    }

    @Test
    @DisplayName("Employee Summary: returns strictly scoped metrics for employee's own documents")
    void testGetSummary_EmployeeRole() {
        when(userRepository.findByEmail("emp@securevault.com")).thenReturn(Optional.of(employeeUser));
        when(documentRepository.countByOwnerIdAndDeletedFalse(10L)).thenReturn(8L);
        when(documentRepository.countByStatusAndOwnerIdAndDeletedFalse(DocumentStatus.PENDING, 10L)).thenReturn(2L);
        when(documentRepository.countByStatusAndOwnerIdAndDeletedFalse(DocumentStatus.APPROVED, 10L)).thenReturn(5L);
        when(documentRepository.countByStatusAndOwnerIdAndDeletedFalse(DocumentStatus.REJECTED, 10L)).thenReturn(1L);
        when(documentRepository.countVerifiedDocumentsByOwnerId(10L)).thenReturn(4L);
        when(documentVersionRepository.sumStorageUsedBytesByOwnerId(10L)).thenReturn(5242880L); // 5 MB

        AnalyticsSummaryDTO summary = analyticsService.getSummary("emp@securevault.com", Set.of("EMPLOYEE"));

        assertNotNull(summary);
        assertEquals(8L, summary.getTotalDocuments(), "Employee sees only their 8 documents, not system-wide total");
        assertEquals(2L, summary.getPendingApprovals());
        assertEquals(5L, summary.getApprovedDocuments());
        assertEquals(1L, summary.getRejectedDocuments());
        assertEquals(4L, summary.getVerifiedDocuments());
        assertEquals(1L, summary.getActiveUsers());
        assertEquals(5242880L, summary.getStorageUsedBytes());
    }

    @Test
    @DisplayName("Reviewer Summary: returns approval queue counts and reviewer decisions")
    void testGetSummary_ReviewerRole() {
        when(userRepository.findByEmail("rev@securevault.com")).thenReturn(Optional.of(reviewerUser));
        when(approvalRepository.countByStatus(ApprovalStatus.PENDING)).thenReturn(12L);
        when(approvalRepository.countByReviewerIdAndStatus(20L, ApprovalStatus.APPROVED)).thenReturn(30L);
        when(approvalRepository.countByReviewerIdAndStatus(20L, ApprovalStatus.REJECTED)).thenReturn(8L);
        when(documentRepository.countVerifiedDocuments()).thenReturn(28L);

        AnalyticsSummaryDTO summary = analyticsService.getSummary("rev@securevault.com", Set.of("REVIEWER"));

        assertNotNull(summary);
        assertEquals(12L, summary.getPendingApprovals());
        assertEquals(30L, summary.getApprovedDocuments());
        assertEquals(8L, summary.getRejectedDocuments());
        assertEquals(50L, summary.getTotalDocuments(), "12 pending + 30 approved + 8 rejected = 50 total queue items");
        assertEquals(28L, summary.getVerifiedDocuments());
    }

    @Test
    @DisplayName("Verifier Access Denied: throws 403 Forbidden")
    void testVerifierAccessDenied() {
        assertThrows(ForbiddenException.class, () ->
                analyticsService.getSummary("verifier@securevault.com", Set.of("VERIFIER")));

        assertThrows(ForbiddenException.class, () ->
                analyticsService.getUploadsByMonth("verifier@securevault.com", Set.of("VERIFIER")));

        assertThrows(ForbiddenException.class, () ->
                analyticsService.getApprovalsByMonth("verifier@securevault.com", Set.of("VERIFIER")));

        assertThrows(ForbiddenException.class, () ->
                analyticsService.getDocumentsByCategory("verifier@securevault.com", Set.of("VERIFIER")));

        assertThrows(ForbiddenException.class, () ->
                analyticsService.getRecentActivity("verifier@securevault.com", Set.of("VERIFIER")));
    }

    @Test
    @DisplayName("Uploads by Month: zero-fills all 12 months for missing periods")
    void testGetUploadsByMonth_ZeroFillsMissingMonths() {
        YearMonth now = YearMonth.now();
        // Return 1 entry for current month and 1 entry for 2 months ago
        Object[] row1 = new Object[]{now.getYear(), now.getMonthValue(), 14L};
        YearMonth twoMonthsAgo = now.minusMonths(2);
        Object[] row2 = new Object[]{twoMonthsAgo.getYear(), twoMonthsAgo.getMonthValue(), 7L};

        when(documentRepository.countUploadsByMonth(any(LocalDateTime.class)))
                .thenReturn(List.of(row2, row1));

        List<MonthlyCountDTO> months = analyticsService.getUploadsByMonth("admin@securevault.com", Set.of("ADMIN"));

        assertNotNull(months);
        assertEquals(12, months.size(), "Must strictly return exactly 12 months");

        // Verify current month has count 14
        MonthlyCountDTO currentMonthDTO = months.get(11);
        assertEquals(14L, currentMonthDTO.getCount());

        // Verify month -2 has count 7
        MonthlyCountDTO twoMonthsAgoDTO = months.get(9);
        assertEquals(7L, twoMonthsAgoDTO.getCount());

        // Verify other months are zero-filled
        MonthlyCountDTO oneMonthAgoDTO = months.get(10);
        assertEquals(0L, oneMonthAgoDTO.getCount(), "Missing month must be zero-filled");
        MonthlyCountDTO oldestMonthDTO = months.get(0);
        assertEquals(0L, oldestMonthDTO.getCount(), "Missing month must be zero-filled");
    }

    @Test
    @DisplayName("Approvals by Month: zero-fills all 12 months with approved and rejected counts")
    void testGetApprovalsByMonth_ZeroFills() {
        YearMonth now = YearMonth.now();
        Object[] rowApproved = new Object[]{now.getYear(), now.getMonthValue(), ApprovalStatus.APPROVED, 9L};
        Object[] rowRejected = new Object[]{now.getYear(), now.getMonthValue(), ApprovalStatus.REJECTED, 3L};

        when(approvalRepository.countApprovalsByMonth(any(LocalDateTime.class)))
                .thenReturn(List.of(rowApproved, rowRejected));

        List<MonthlyApprovalDTO> months = analyticsService.getApprovalsByMonth("admin@securevault.com", Set.of("ADMIN"));

        assertNotNull(months);
        assertEquals(12, months.size());

        MonthlyApprovalDTO latest = months.get(11);
        assertEquals(9L, latest.getApproved());
        assertEquals(3L, latest.getRejected());

        // Previous month is zero-filled
        MonthlyApprovalDTO prev = months.get(10);
        assertEquals(0L, prev.getApproved());
        assertEquals(0L, prev.getRejected());
    }

    @Test
    @DisplayName("Documents by Category: returns category aggregation")
    void testGetDocumentsByCategory() {
        Object[] c1 = new Object[]{"LEGAL", 25L};
        Object[] c2 = new Object[]{"FINANCIAL", 18L};
        Object[] c3 = new Object[]{"ACADEMIC", 7L};

        when(documentRepository.countDocumentsByCategory()).thenReturn(List.of(c1, c2, c3));

        List<CategoryCountDTO> categories = analyticsService.getDocumentsByCategory("admin@securevault.com", Set.of("ADMIN"));

        assertNotNull(categories);
        assertEquals(3, categories.size());
        assertEquals("LEGAL", categories.get(0).getCategory());
        assertEquals(25L, categories.get(0).getCount());
        assertEquals("FINANCIAL", categories.get(1).getCategory());
        assertEquals(18L, categories.get(1).getCount());
    }

    @Test
    @DisplayName("Recent Activity: returns top 10 audit logs according to role")
    void testGetRecentActivity_Admin() {
        AuditLog log1 = new AuditLog(employeeUser, "UPLOAD", "DOCUMENT", 101L, "127.0.0.1", "Uploaded passport");
        log1.setId(1L);
        log1.setTimestamp(LocalDateTime.now());

        AuditLog log2 = new AuditLog(reviewerUser, "APPROVE", "DOCUMENT_APPROVAL", 201L, "127.0.0.1", "Approved passport");
        log2.setId(2L);
        log2.setTimestamp(LocalDateTime.now().minusMinutes(5));

        when(auditLogRepository.findTop10ByOrderByTimestampDesc()).thenReturn(List.of(log1, log2));

        List<AuditLogDTO> recent = analyticsService.getRecentActivity("admin@securevault.com", Set.of("ADMIN"));

        assertNotNull(recent);
        assertEquals(2, recent.size());
        assertEquals("UPLOAD", recent.get(0).getAction());
        assertEquals("APPROVE", recent.get(1).getAction());
    }
}
