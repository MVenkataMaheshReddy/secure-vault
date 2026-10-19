package com.securevault.dto;

import com.securevault.entity.SharePermission;
import java.time.LocalDateTime;

public class SharedDocumentDTO {

    private Long shareId;
    private Long documentId;
    private String documentTitle;
    private String category;
    private String description;
    private String originalFileName;
    private Integer currentVersion;
    private String status;
    private String sharedByName;
    private String sharedByEmail;
    private SharePermission permission;
    private LocalDateTime expiresAt;
    private LocalDateTime sharedAt;
    private Long fileSize;
    private String contentType;

    public SharedDocumentDTO() {}

    public SharedDocumentDTO(Long shareId, Long documentId, String documentTitle,
                             String category, String description, String originalFileName,
                             Integer currentVersion, String status, String sharedByName,
                             String sharedByEmail, SharePermission permission,
                             LocalDateTime expiresAt, LocalDateTime sharedAt,
                             Long fileSize, String contentType) {
        this.shareId = shareId;
        this.documentId = documentId;
        this.documentTitle = documentTitle;
        this.category = category;
        this.description = description;
        this.originalFileName = originalFileName;
        this.currentVersion = currentVersion;
        this.status = status;
        this.sharedByName = sharedByName;
        this.sharedByEmail = sharedByEmail;
        this.permission = permission;
        this.expiresAt = expiresAt;
        this.sharedAt = sharedAt;
        this.fileSize = fileSize;
        this.contentType = contentType;
    }

    public Long getShareId() {
        return shareId;
    }

    public void setShareId(Long shareId) {
        this.shareId = shareId;
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

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getOriginalFileName() {
        return originalFileName;
    }

    public void setOriginalFileName(String originalFileName) {
        this.originalFileName = originalFileName;
    }

    public Integer getCurrentVersion() {
        return currentVersion;
    }

    public void setCurrentVersion(Integer currentVersion) {
        this.currentVersion = currentVersion;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getSharedByName() {
        return sharedByName;
    }

    public void setSharedByName(String sharedByName) {
        this.sharedByName = sharedByName;
    }

    public String getSharedByEmail() {
        return sharedByEmail;
    }

    public void setSharedByEmail(String sharedByEmail) {
        this.sharedByEmail = sharedByEmail;
    }

    public SharePermission getPermission() {
        return permission;
    }

    public void setPermission(SharePermission permission) {
        this.permission = permission;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(LocalDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }

    public LocalDateTime getSharedAt() {
        return sharedAt;
    }

    public void setSharedAt(LocalDateTime sharedAt) {
        this.sharedAt = sharedAt;
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
}
