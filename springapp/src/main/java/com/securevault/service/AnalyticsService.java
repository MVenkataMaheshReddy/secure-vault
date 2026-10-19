package com.securevault.service;

import com.securevault.dto.AnalyticsSummaryDTO;
import com.securevault.dto.AuditLogDTO;
import com.securevault.dto.CategoryCountDTO;
import com.securevault.dto.MonthlyApprovalDTO;
import com.securevault.dto.MonthlyCountDTO;
import com.securevault.entity.ApprovalStatus;
import com.securevault.entity.AuditLog;
import com.securevault.entity.DocumentStatus;
import com.securevault.entity.User;
import com.securevault.exception.ForbiddenException;
import com.securevault.exception.ResourceNotFoundException;
import com.securevault.repository.ApprovalRepository;
import com.securevault.repository.AuditLogRepository;
import com.securevault.repository.DocumentRepository;
import com.securevault.repository.DocumentVersionRepository;
import com.securevault.repository.UserRepository;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AnalyticsService {

    private final DocumentRepository documentRepository;
    private final DocumentVersionRepository documentVersionRepository;
    private final ApprovalRepository approvalRepository;
    private final AuditLogRepository auditLogRepository;
    private final UserRepository userRepository;

    public AnalyticsService(DocumentRepository documentRepository,
                            DocumentVersionRepository documentVersionRepository,
                            ApprovalRepository approvalRepository,
                            AuditLogRepository auditLogRepository,
                            UserRepository userRepository) {
        this.documentRepository = documentRepository;
        this.documentVersionRepository = documentVersionRepository;
        this.approvalRepository = approvalRepository;
        this.auditLogRepository = auditLogRepository;
        this.userRepository = userRepository;
    }

    private User getUserByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with email: " + email));
    }

    private void validateRoleAccess(Set<String> roles) {
        boolean hasAdmin = roles.contains("ADMIN");
        boolean hasEmployee = roles.contains("EMPLOYEE");
        boolean hasReviewer = roles.contains("REVIEWER");
        boolean hasVerifierOnly = roles.contains("VERIFIER") && !hasAdmin && !hasEmployee && !hasReviewer;

        if (hasVerifierOnly || (!hasAdmin && !hasEmployee && !hasReviewer)) {
            throw new ForbiddenException("Verifiers do not have permission to access analytics dashboard");
        }
    }

    // =========================================================================
    // 1. SUMMARY STATS (GET /api/analytics/summary)
    // =========================================================================
    @Transactional(readOnly = true)
    public AnalyticsSummaryDTO getSummary(String userEmail, Set<String> roles) {
        validateRoleAccess(roles);

        if (roles.contains("ADMIN")) {
            long totalDocs = documentRepository.countByDeletedFalse();
            long pending = documentRepository.countByStatusAndDeletedFalse(DocumentStatus.PENDING);
            long approved = documentRepository.countByStatusAndDeletedFalse(DocumentStatus.APPROVED);
            long rejected = documentRepository.countByStatusAndDeletedFalse(DocumentStatus.REJECTED);
            long verified = documentRepository.countVerifiedDocuments();
            LocalDateTime thirtyDaysAgo = LocalDateTime.now().minusDays(30);
            long activeUsers = auditLogRepository.countDistinctActiveUsers(thirtyDaysAgo);
            long storageBytes = documentVersionRepository.sumStorageUsedBytes();

            return new AnalyticsSummaryDTO(totalDocs, pending, approved, rejected, verified, activeUsers, storageBytes);
        } else if (roles.contains("EMPLOYEE")) {
            User user = getUserByEmail(userEmail);
            long totalDocs = documentRepository.countByOwnerIdAndDeletedFalse(user.getId());
            long pending = documentRepository.countByStatusAndOwnerIdAndDeletedFalse(DocumentStatus.PENDING, user.getId());
            long approved = documentRepository.countByStatusAndOwnerIdAndDeletedFalse(DocumentStatus.APPROVED, user.getId());
            long rejected = documentRepository.countByStatusAndOwnerIdAndDeletedFalse(DocumentStatus.REJECTED, user.getId());
            long verified = documentRepository.countVerifiedDocumentsByOwnerId(user.getId());
            long storageBytes = documentVersionRepository.sumStorageUsedBytesByOwnerId(user.getId());

            return new AnalyticsSummaryDTO(totalDocs, pending, approved, rejected, verified, 1L, storageBytes);
        } else if (roles.contains("REVIEWER")) {
            User user = getUserByEmail(userEmail);
            long pendingQueue = approvalRepository.countByStatus(ApprovalStatus.PENDING);
            long approvedByMe = approvalRepository.countByReviewerIdAndStatus(user.getId(), ApprovalStatus.APPROVED);
            long rejectedByMe = approvalRepository.countByReviewerIdAndStatus(user.getId(), ApprovalStatus.REJECTED);
            long totalQueueAndDecisions = pendingQueue + approvedByMe + rejectedByMe;
            long verified = documentRepository.countVerifiedDocuments();

            return new AnalyticsSummaryDTO(totalQueueAndDecisions, pendingQueue, approvedByMe, rejectedByMe, verified, 1L, 0L);
        }

        throw new ForbiddenException("Access denied");
    }

    // =========================================================================
    // 2. UPLOADS BY MONTH (GET /api/analytics/uploads-by-month)
    // =========================================================================
    @Transactional(readOnly = true)
    public List<MonthlyCountDTO> getUploadsByMonth(String userEmail, Set<String> roles) {
        validateRoleAccess(roles);

        YearMonth currentYearMonth = YearMonth.now();
        YearMonth startYearMonth = currentYearMonth.minusMonths(11);
        LocalDateTime since = startYearMonth.atDay(1).atStartOfDay();

        List<Object[]> rawCounts;
        if (roles.contains("ADMIN") || roles.contains("REVIEWER")) {
            rawCounts = documentRepository.countUploadsByMonth(since);
        } else {
            User user = getUserByEmail(userEmail);
            rawCounts = documentRepository.countUploadsByMonthAndOwnerId(user.getId(), since);
        }

        Map<String, Long> countMap = new HashMap<>();
        if (rawCounts != null) {
            for (Object[] row : rawCounts) {
                if (row.length >= 3 && row[0] instanceof Number && row[1] instanceof Number && row[2] instanceof Number) {
                    int year = ((Number) row[0]).intValue();
                    int month = ((Number) row[1]).intValue();
                    long count = ((Number) row[2]).longValue();
                    String key = String.format("%04d-%02d", year, month);
                    countMap.put(key, count);
                }
            }
        }

        DateTimeFormatter labelFormatter = DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH);
        List<MonthlyCountDTO> result = new ArrayList<>();
        for (int i = 11; i >= 0; i--) {
            YearMonth ym = currentYearMonth.minusMonths(i);
            String monthKey = String.format("%04d-%02d", ym.getYear(), ym.getMonthValue());
            String label = ym.format(labelFormatter);
            long count = countMap.getOrDefault(monthKey, 0L);
            result.add(new MonthlyCountDTO(monthKey, label, count));
        }

        return result;
    }

    // =========================================================================
    // 3. APPROVALS BY MONTH (GET /api/analytics/approvals-by-month)
    // =========================================================================
    @Transactional(readOnly = true)
    public List<MonthlyApprovalDTO> getApprovalsByMonth(String userEmail, Set<String> roles) {
        validateRoleAccess(roles);

        YearMonth currentYearMonth = YearMonth.now();
        YearMonth startYearMonth = currentYearMonth.minusMonths(11);
        LocalDateTime since = startYearMonth.atDay(1).atStartOfDay();

        List<Object[]> rawRows;
        if (roles.contains("ADMIN")) {
            rawRows = approvalRepository.countApprovalsByMonth(since);
        } else if (roles.contains("REVIEWER")) {
            User user = getUserByEmail(userEmail);
            rawRows = approvalRepository.countApprovalsByMonthAndReviewerId(user.getId(), since);
        } else {
            User user = getUserByEmail(userEmail);
            rawRows = approvalRepository.countApprovalsByMonthAndOwnerId(user.getId(), since);
        }

        Map<String, Long> approvedMap = new HashMap<>();
        Map<String, Long> rejectedMap = new HashMap<>();

        if (rawRows != null) {
            for (Object[] row : rawRows) {
                if (row.length >= 4 && row[0] instanceof Number && row[1] instanceof Number && row[3] instanceof Number) {
                    int year = ((Number) row[0]).intValue();
                    int month = ((Number) row[1]).intValue();
                    String key = String.format("%04d-%02d", year, month);
                    long count = ((Number) row[3]).longValue();
                    ApprovalStatus status = (ApprovalStatus) row[2];

                    if (status == ApprovalStatus.APPROVED) {
                        approvedMap.put(key, count);
                    } else if (status == ApprovalStatus.REJECTED) {
                        rejectedMap.put(key, count);
                    }
                }
            }
        }

        DateTimeFormatter labelFormatter = DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH);
        List<MonthlyApprovalDTO> result = new ArrayList<>();
        for (int i = 11; i >= 0; i--) {
            YearMonth ym = currentYearMonth.minusMonths(i);
            String monthKey = String.format("%04d-%02d", ym.getYear(), ym.getMonthValue());
            String label = ym.format(labelFormatter);
            long appCount = approvedMap.getOrDefault(monthKey, 0L);
            long rejCount = rejectedMap.getOrDefault(monthKey, 0L);
            result.add(new MonthlyApprovalDTO(monthKey, label, appCount, rejCount));
        }

        return result;
    }

    // =========================================================================
    // 4. DOCUMENTS BY CATEGORY (GET /api/analytics/documents-by-category)
    // =========================================================================
    @Transactional(readOnly = true)
    public List<CategoryCountDTO> getDocumentsByCategory(String userEmail, Set<String> roles) {
        validateRoleAccess(roles);

        List<Object[]> rawCategories;
        if (roles.contains("ADMIN") || roles.contains("REVIEWER")) {
            rawCategories = documentRepository.countDocumentsByCategory();
        } else {
            User user = getUserByEmail(userEmail);
            rawCategories = documentRepository.countDocumentsByCategoryAndOwnerId(user.getId());
        }

        List<CategoryCountDTO> result = new ArrayList<>();
        if (rawCategories != null) {
            for (Object[] row : rawCategories) {
                if (row.length >= 2 && row[1] instanceof Number) {
                    String category = row[0] != null ? row[0].toString() : "UNCATEGORIZED";
                    long count = ((Number) row[1]).longValue();
                    result.add(new CategoryCountDTO(category, count));
                }
            }
        }

        return result;
    }

    // =========================================================================
    // 5. RECENT ACTIVITY (GET /api/analytics/recent-activity)
    // =========================================================================
    @Transactional(readOnly = true)
    public List<AuditLogDTO> getRecentActivity(String userEmail, Set<String> roles) {
        validateRoleAccess(roles);

        List<AuditLog> logs;
        if (roles.contains("ADMIN")) {
            logs = auditLogRepository.findTop10ByOrderByTimestampDesc();
        } else if (roles.contains("REVIEWER")) {
            logs = auditLogRepository.findTop10ByActionInOrderByTimestampDesc(List.of("APPROVE", "REJECT", "VERIFY"));
        } else {
            User user = getUserByEmail(userEmail);
            logs = auditLogRepository.findTop10ByUserIdOrderByTimestampDesc(user.getId());
        }

        if (logs == null) return List.of();

        return logs.stream().map(log -> new AuditLogDTO(
                log.getId(),
                log.getUser() != null ? log.getUser().getId() : null,
                log.getUser() != null ? log.getUser().getName() : "System / Public",
                log.getUser() != null ? log.getUser().getEmail() : "Anonymous",
                log.getAction(),
                log.getEntityType(),
                log.getEntityId(),
                log.getIpAddress(),
                log.getDetails(),
                log.getTimestamp()
        )).collect(Collectors.toList());
    }
}
