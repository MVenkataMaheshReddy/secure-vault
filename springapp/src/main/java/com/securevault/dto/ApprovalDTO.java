package com.securevault.dto;

import com.securevault.entity.ApprovalStatus;
import java.time.LocalDateTime;

public class ApprovalDTO {

    private Long id;
    private Long documentId;
    private String documentTitle;
    private String category;
    private String ownerName;
    private String ownerEmail;
    private Integer version;
    private ApprovalStatus status;
    private Long reviewerId;
    private String reviewerName;
    private String reviewerEmail;
    private String comments;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String originalFileName;
    private String fileType;

    public ApprovalDTO() {}

    public ApprovalDTO(Long id, Long documentId, String documentTitle, String category,
                       String ownerName, String ownerEmail, Integer version, ApprovalStatus status,
                       Long reviewerId, String reviewerName, String reviewerEmail,
                       String comments, LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.documentId = documentId;
        this.documentTitle = documentTitle;
        this.category = category;
        this.ownerName = ownerName;
        this.ownerEmail = ownerEmail;
        this.version = version;
        this.status = status;
        this.reviewerId = reviewerId;
        this.reviewerName = reviewerName;
        this.reviewerEmail = reviewerEmail;
        this.comments = comments;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getDocumentId() {
        return documentId;
    }

    public void setDocumentId(Long documentId) {
        this.documentId = documentId;
    }

    public String getDocumentTitle() {
        return documentTitle;
    }

    public void setDocumentTitle(String documentTitle) {
        this.documentTitle = documentTitle;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getOwnerName() {
        return ownerName;
    }

    public void setOwnerName(String ownerName) {
        this.ownerName = ownerName;
    }

    public String getOwnerEmail() {
        return ownerEmail;
    }

    public void setOwnerEmail(String ownerEmail) {
        this.ownerEmail = ownerEmail;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    public ApprovalStatus getStatus() {
        return status;
    }

    public void setStatus(ApprovalStatus status) {
        this.status = status;
    }

    public Long getReviewerId() {
        return reviewerId;
    }

    public void setReviewerId(Long reviewerId) {
        this.reviewerId = reviewerId;
    }

    public String getReviewerName() {
        return reviewerName;
    }

    public void setReviewerName(String reviewerName) {
        this.reviewerName = reviewerName;
    }

    public String getReviewerEmail() {
        return reviewerEmail;
    }

    public void setReviewerEmail(String reviewerEmail) {
        this.reviewerEmail = reviewerEmail;
    }

    public String getComments() {
        return comments;
    }

    public void setComments(String comments) {
        this.comments = comments;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getOriginalFileName() {
        return originalFileName;
    }

    public void setOriginalFileName(String originalFileName) {
        this.originalFileName = originalFileName;
    }

    public String getFileType() {
        return fileType;
    }

    public void setFileType(String fileType) {
        this.fileType = fileType;
    }
}
