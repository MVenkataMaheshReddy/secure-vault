package com.securevault.dto;

public class AnalyticsSummaryDTO {

    private Long totalDocuments;
    private Long pendingApprovals;
    private Long approvedDocuments;
    private Long rejectedDocuments;
    private Long verifiedDocuments;
    private Long activeUsers;
    private Long storageUsedBytes;

    public AnalyticsSummaryDTO() {
        this.totalDocuments = 0L;
        this.pendingApprovals = 0L;
        this.approvedDocuments = 0L;
        this.rejectedDocuments = 0L;
        this.verifiedDocuments = 0L;
        this.activeUsers = 0L;
        this.storageUsedBytes = 0L;
    }

    public AnalyticsSummaryDTO(Long totalDocuments, Long pendingApprovals, Long approvedDocuments,
                               Long rejectedDocuments, Long verifiedDocuments, Long activeUsers,
                               Long storageUsedBytes) {
        this.totalDocuments = totalDocuments != null ? totalDocuments : 0L;
        this.pendingApprovals = pendingApprovals != null ? pendingApprovals : 0L;
        this.approvedDocuments = approvedDocuments != null ? approvedDocuments : 0L;
        this.rejectedDocuments = rejectedDocuments != null ? rejectedDocuments : 0L;
        this.verifiedDocuments = verifiedDocuments != null ? verifiedDocuments : 0L;
        this.activeUsers = activeUsers != null ? activeUsers : 0L;
        this.storageUsedBytes = storageUsedBytes != null ? storageUsedBytes : 0L;
    }

    public Long getTotalDocuments() {
        return totalDocuments;
    }

    public void setTotalDocuments(Long totalDocuments) {
        this.totalDocuments = totalDocuments;
    }

    public Long getPendingApprovals() {
        return pendingApprovals;
    }

    public void setPendingApprovals(Long pendingApprovals) {
        this.pendingApprovals = pendingApprovals;
    }

    public Long getApprovedDocuments() {
        return approvedDocuments;
    }

    public void setApprovedDocuments(Long approvedDocuments) {
        this.approvedDocuments = approvedDocuments;
    }

    public Long getRejectedDocuments() {
        return rejectedDocuments;
    }

    public void setRejectedDocuments(Long rejectedDocuments) {
        this.rejectedDocuments = rejectedDocuments;
    }

    public Long getVerifiedDocuments() {
        return verifiedDocuments;
    }

    public void setVerifiedDocuments(Long verifiedDocuments) {
        this.verifiedDocuments = verifiedDocuments;
    }

    public Long getActiveUsers() {
        return activeUsers;
    }

    public void setActiveUsers(Long activeUsers) {
        this.activeUsers = activeUsers;
    }

    public Long getStorageUsedBytes() {
        return storageUsedBytes;
    }

    public void setStorageUsedBytes(Long storageUsedBytes) {
        this.storageUsedBytes = storageUsedBytes;
    }
}
