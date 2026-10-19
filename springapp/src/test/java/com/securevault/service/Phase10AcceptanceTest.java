package com.securevault.service;

import com.securevault.dto.AnalyticsSummaryDTO;
import com.securevault.dto.AuditLogDTO;
import com.securevault.dto.CategoryCountDTO;
import com.securevault.dto.MonthlyApprovalDTO;
import com.securevault.dto.MonthlyCountDTO;
import com.securevault.entity.ApprovalStatus;
import com.securevault.entity.AuditLog;
import com.securevault.entity.DocumentStatus;
import com.securevault.entity.Role;
import com.securevault.entity.User;
import com.securevault.exception.ForbiddenException;
import com.securevault.repository.ApprovalRepository;
import com.securevault.repository.AuditLogRepository;
import com.securevault.repository.DocumentRepository;
import com.securevault.repository.DocumentVersionRepository;
import com.securevault.repository.UserRepository;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
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
public class Phase10AcceptanceTest {

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

    private User adminUser;
    private User employeeUser;
    private User reviewerUser;
    private User verifierUser;

    @BeforeEach
    void setUp() {
        analyticsService = new AnalyticsService(
                documentRepository,
                documentVersionRepository,
                approvalRepository,
                auditLogRepository,
                userRepository
        );

        Role adminRole = new Role();
        adminRole.setName(com.securevault.entity.RoleName.ADMIN);
        adminUser = new User();
        adminUser.setId(1L);
        adminUser.setEmail("admin@securevault.com");
        adminUser.setName("Admin User");
        adminUser.setRoles(Set.of(adminRole));

        Role employeeRole = new Role();
        employeeRole.setName(com.securevault.entity.RoleName.EMPLOYEE);
        employeeUser = new User();
        employeeUser.setId(2L);
        employeeUser.setEmail("employee@securevault.com");
        employeeUser.setName("Employee User");
        employeeUser.setRoles(Set.of(employeeRole));

        Role reviewerRole = new Role();
        reviewerRole.setName(com.securevault.entity.RoleName.REVIEWER);
        reviewerUser = new User();
        reviewerUser.setId(3L);
        reviewerUser.setEmail("reviewer@securevault.com");
        reviewerUser.setName("Reviewer User");
        reviewerUser.setRoles(Set.of(reviewerRole));

        Role verifierRole = new Role();
        verifierRole.setName(com.securevault.entity.RoleName.VERIFIER);
        verifierUser = new User();
        verifierUser.setId(4L);
        verifierUser.setEmail("verifier@securevault.com");
        verifierUser.setName("Verifier User");
        verifierUser.setRoles(Set.of(verifierRole));
    }

    @Test
    @DisplayName("Admin Summary: returns system-wide metrics matching direct counts")
    void testAdminSummaryMatchesSystemWideCounts() {
        when(documentRepository.countByDeletedFalse()).thenReturn(150L);
        when(documentRepository.countByStatusAndDeletedFalse(DocumentStatus.PENDING)).thenReturn(12L);
        when(documentRepository.countByStatusAndDeletedFalse(DocumentStatus.APPROVED)).thenReturn(110L);
        when(documentRepository.countByStatusAndDeletedFalse(DocumentStatus.REJECTED)).thenReturn(15L);
        when(documentRepository.countVerifiedDocuments()).thenReturn(85L);
        when(auditLogRepository.countDistinctActiveUsers(any(LocalDateTime.class))).thenReturn(42L);
        when(documentVersionRepository.sumStorageUsedBytes()).thenReturn(524288000L); // 500MB

        AnalyticsSummaryDTO summary = analyticsService.getSummary("admin@securevault.com", Set.of("ADMIN"));

        assertNotNull(summary);
        assertEquals(150L, summary.getTotalDocuments());
        assertEquals(12L, summary.getPendingApprovals());
        assertEquals(110L, summary.getApprovedDocuments());
        assertEquals(15L, summary.getRejectedDocuments());
        assertEquals(85L, summary.getVerifiedDocuments());
        assertEquals(42L, summary.getActiveUsers());
        assertEquals(524288000L, summary.getStorageUsedBytes());
    }

    @Test
    @DisplayName("Employee Summary: scoped ONLY to their own documents")
    void testEmployeeSummaryScopedToOwnDocuments() {
        when(userRepository.findByEmail("employee@securevault.com")).thenReturn(Optional.of(employeeUser));
        when(documentRepository.countByOwnerIdAndDeletedFalse(2L)).thenReturn(25L);
        when(documentRepository.countByStatusAndOwnerIdAndDeletedFalse(DocumentStatus.PENDING, 2L)).thenReturn(3L);
        when(documentRepository.countByStatusAndOwnerIdAndDeletedFalse(DocumentStatus.APPROVED, 2L)).thenReturn(18L);
        when(documentRepository.countByStatusAndOwnerIdAndDeletedFalse(DocumentStatus.REJECTED, 2L)).thenReturn(4L);
        when(documentRepository.countVerifiedDocumentsByOwnerId(2L)).thenReturn(15L);
        when(documentVersionRepository.sumStorageUsedBytesByOwnerId(2L)).thenReturn(10485760L); // 10MB

        AnalyticsSummaryDTO summary = analyticsService.getSummary("employee@securevault.com", Set.of("EMPLOYEE"));

        assertNotNull(summary);
        assertEquals(25L, summary.getTotalDocuments());
        assertEquals(3L, summary.getPendingApprovals());
        assertEquals(18L, summary.getApprovedDocuments());
        assertEquals(4L, summary.getRejectedDocuments());
        assertEquals(15L, summary.getVerifiedDocuments());
        assertEquals(10485760L, summary.getStorageUsedBytes());

        // Verify system-wide counts were NEVER called
        verify(documentRepository, never()).countByDeletedFalse();
        verify(documentVersionRepository, never()).sumStorageUsedBytes();
        verify(auditLogRepository, never()).countDistinctActiveUsers(any());
    }

    @Test
    @DisplayName("Reviewer Summary: scoped to approval queue stats and their own decisions")
    void testReviewerSummaryScopedToApprovalQueue() {
        when(userRepository.findByEmail("reviewer@securevault.com")).thenReturn(Optional.of(reviewerUser));
        when(approvalRepository.countByStatus(ApprovalStatus.PENDING)).thenReturn(8L);
        when(approvalRepository.countByReviewerIdAndStatus(3L, ApprovalStatus.APPROVED)).thenReturn(45L);
        when(approvalRepository.countByReviewerIdAndStatus(3L, ApprovalStatus.REJECTED)).thenReturn(6L);
        when(documentRepository.countVerifiedDocuments()).thenReturn(30L);

        AnalyticsSummaryDTO summary = analyticsService.getSummary("reviewer@securevault.com", Set.of("REVIEWER"));

        assertNotNull(summary);
        assertEquals(8L, summary.getPendingApprovals());
        assertEquals(45L, summary.getApprovedDocuments());
        assertEquals(6L, summary.getRejectedDocuments());
        assertEquals(8L + 45L + 6L, summary.getTotalDocuments());
        assertEquals(30L, summary.getVerifiedDocuments());
    }

    @Test
    @DisplayName("Verifier Access: strictly throws 403 Forbidden")
    void testVerifierAccessForbidden() {
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
    @DisplayName("Monthly Uploads: 12 months exactly returned, with zero-fill for inactive months")
    void testMonthlyUploadsZeroFilled() {
        YearMonth now = YearMonth.now();
        int currentYear = now.getYear();
        int currentMonth = now.getMonthValue();
        String currentMonthKey = String.format("%04d-%02d", currentYear, currentMonth);

        // Simulate DB returning data for only current month out of 12
        List<Object[]> dbResults = new ArrayList<>();
        dbResults.add(new Object[]{currentYear, currentMonth, 28L});
        when(documentRepository.countUploadsByMonth(any(LocalDateTime.class))).thenReturn(dbResults);

        List<MonthlyCountDTO> result = analyticsService.getUploadsByMonth("admin@securevault.com", Set.of("ADMIN"));

        assertNotNull(result);
        assertEquals(12, result.size(), "Result must contain exactly 12 months");

        // The last item is the current month
        MonthlyCountDTO lastMonth = result.get(11);
        assertEquals(currentMonthKey, lastMonth.getMonth());
        assertEquals(28L, lastMonth.getCount());

        // Previous months should be zero-filled
        for (int i = 0; i < 11; i++) {
            assertEquals(0L, result.get(i).getCount(), "Missing month at index " + i + " must be zero-filled");
            assertNotNull(result.get(i).getLabel());
        }
    }

    @Test
    @DisplayName("Monthly Approvals: 12 months zero-filled for both approved and rejected counts")
    void testMonthlyApprovalsZeroFilled() {
        YearMonth now = YearMonth.now();
        int currentYear = now.getYear();
        int currentMonth = now.getMonthValue();
        String currentMonthKey = String.format("%04d-%02d", currentYear, currentMonth);

        List<Object[]> dbResults = new ArrayList<>();
        dbResults.add(new Object[]{currentYear, currentMonth, ApprovalStatus.APPROVED, 14L});
        dbResults.add(new Object[]{currentYear, currentMonth, ApprovalStatus.REJECTED, 3L});
        when(approvalRepository.countApprovalsByMonth(any(LocalDateTime.class))).thenReturn(dbResults);

        List<MonthlyApprovalDTO> result = analyticsService.getApprovalsByMonth("admin@securevault.com", Set.of("ADMIN"));

        assertEquals(12, result.size());
        MonthlyApprovalDTO lastMonth = result.get(11);
        assertEquals(currentMonthKey, lastMonth.getMonth());
        assertEquals(14L, lastMonth.getApproved());
        assertEquals(3L, lastMonth.getRejected());

        // First month should have 0 approved and 0 rejected
        MonthlyApprovalDTO firstMonth = result.get(0);
        assertEquals(0L, firstMonth.getApproved());
        assertEquals(0L, firstMonth.getRejected());
    }

    @Test
    @DisplayName("Documents By Category: returns category counts correctly")
    void testDocumentsByCategory() {
        List<Object[]> dbResults = new ArrayList<>();
        dbResults.add(new Object[]{"FINANCIAL", 35L});
        dbResults.add(new Object[]{"LEGAL", 20L});
        dbResults.add(new Object[]{"IDENTITY", 15L});
        when(documentRepository.countDocumentsByCategory()).thenReturn(dbResults);

        List<CategoryCountDTO> categories = analyticsService.getDocumentsByCategory("admin@securevault.com", Set.of("ADMIN"));

        assertEquals(3, categories.size());
        assertEquals("FINANCIAL", categories.get(0).getCategory());
        assertEquals(35L, categories.get(0).getCount());
    }

    @Test
    @DisplayName("Recent Activity: Employee receives only their own audit logs (max 10)")
    void testRecentActivityEmployeeScoped() {
        when(userRepository.findByEmail("employee@securevault.com")).thenReturn(Optional.of(employeeUser));

        AuditLog log1 = new AuditLog();
        log1.setId(101L);
        log1.setUser(employeeUser);
        log1.setAction("UPLOAD");
        log1.setEntityType("DOCUMENT");
        log1.setEntityId(55L);
        log1.setIpAddress("192.168.1.10");
        log1.setDetails("Uploaded contract");
        log1.setTimestamp(LocalDateTime.now());

        when(auditLogRepository.findTop10ByUserIdOrderByTimestampDesc(2L)).thenReturn(List.of(log1));

        List<AuditLogDTO> activities = analyticsService.getRecentActivity("employee@securevault.com", Set.of("EMPLOYEE"));

        assertEquals(1, activities.size());
        assertEquals("UPLOAD", activities.get(0).getAction());
        assertEquals("Employee User", activities.get(0).getUserName());
        verify(auditLogRepository, never()).findTop10ByOrderByTimestampDesc();
    }
}
