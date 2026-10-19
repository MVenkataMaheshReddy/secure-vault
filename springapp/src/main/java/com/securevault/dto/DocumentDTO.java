package com.securevault.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDateTime;
import java.util.Set;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class DocumentDTO {

    private Long id;
    private String title;
    private String originalFileName;
    private String category;
    private String description;
    private Long userId;
    private Long folderId;
    private String status;
    private Integer currentVersion;
    private String verificationId;
    private String verificationStatus;
    private Long fileSize;
    private String contentType;
    private String checksum;
    private Set<String> tags;
    private String ownerEmail;
    private boolean deleted;
    private LocalDateTime deletedAt;
    private com.securevault.entity.OcrStatus ocrStatus;
    private String ocrErrorMessage;
    private String rejectionComment;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public DocumentDTO() {}

    public DocumentDTO(Long id, String title, String originalFileName, String category,
                       String description, Long userId, Long folderId, String status,
                       Integer currentVersion, String verificationId, Long fileSize,
                       String contentType, String checksum, LocalDateTime createdAt,
                       LocalDateTime updatedAt) {
        this.id = id;
        this.title = title;
        this.originalFileName = originalFileName;
        this.category = category;
        this.description = description;
        this.userId = userId;
        this.folderId = folderId;
        this.status = status;
        this.currentVersion = currentVersion;
        this.verificationId = verificationId;
        this.fileSize = fileSize;
        this.contentType = contentType;
        this.checksum = checksum;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getOriginalFileName() {
        return originalFileName;
    }

    public void setOriginalFileName(String originalFileName) {
        this.originalFileName = originalFileName;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Long getFolderId() {
        return folderId;
    }

    public void setFolderId(Long folderId) {
        this.folderId = folderId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getCurrentVersion() {
        return currentVersion;
    }

    public void setCurrentVersion(Integer currentVersion) {
        this.currentVersion = currentVersion;
    }

    public String getVerificationId() {
        return verificationId;
    }

    public void setVerificationId(String verificationId) {
        this.verificationId = verificationId;
    }

    public String getVerificationStatus() {
        return verificationStatus;
    }

    public void setVerificationStatus(String verificationStatus) {
        this.verificationStatus = verificationStatus;
    }

    public Long getFileSize() {
        return fileSize;
    }

    public void setFileSize(Long fileSize) {
        this.fileSize = fileSize;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public String getChecksum() {
        return checksum;
    }

    public void setChecksum(String checksum) {
        this.checksum = checksum;
    }

    public Set<String> getTags() {
        return tags;
    }

    public void setTags(Set<String> tags) {
        this.tags = tags;
    }

    public String getOwnerEmail() {
        return ownerEmail;
    }

    public void setOwnerEmail(String ownerEmail) {
        this.ownerEmail = ownerEmail;
    }

    public boolean isDeleted() {
        return deleted;
    }

    public void setDeleted(boolean deleted) {
        this.deleted = deleted;
    }

    public LocalDateTime getDeletedAt() {
        return deletedAt;
    }

    public void setDeletedAt(LocalDateTime deletedAt) {
        this.deletedAt = deletedAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }


    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public com.securevault.entity.OcrStatus getOcrStatus() {
        return ocrStatus;
    }

    public void setOcrStatus(com.securevault.entity.OcrStatus ocrStatus) {
        this.ocrStatus = ocrStatus;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getOcrErrorMessage() {
        return ocrErrorMessage;
    }

    public void setOcrErrorMessage(String ocrErrorMessage) {
        this.ocrErrorMessage = ocrErrorMessage;
    }

    // Compatibility getters & setters for existing React frontend
    public Long getDoc_id() {
        return id;
    }

    public void setDoc_id(Long doc_id) {
        this.id = doc_id;
    }

    public String getDoc_name() {
        return title != null ? title : originalFileName;
    }

    public void setDoc_name(String doc_name) {
        this.title = doc_name;
        if (this.originalFileName == null) {
            this.originalFileName = doc_name;
        }
    }

    public byte[] getFileData() {
        return null;
    }

    public String getRejectionComment() {
        return rejectionComment;
    }

    public void setRejectionComment(String rejectionComment) {
        this.rejectionComment = rejectionComment;
    }
}
