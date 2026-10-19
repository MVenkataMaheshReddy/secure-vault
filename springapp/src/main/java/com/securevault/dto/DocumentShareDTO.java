package com.securevault.dto;

import com.securevault.entity.SharePermission;
import java.time.LocalDateTime;

public class DocumentShareDTO {

    private Long id;
    private Long documentId;
    private String documentTitle;
    private Long sharedById;
    private String sharedByName;
    private String sharedByEmail;
    private Long sharedWithId;
    private String sharedWithName;
    private String sharedWithEmail;
    private SharePermission permission;
    private LocalDateTime expiresAt;
    private LocalDateTime createdAt;
    private boolean expired;
    private boolean revoked;

    public DocumentShareDTO() {}

    public DocumentShareDTO(Long id, Long documentId, String documentTitle,
                            Long sharedById, String sharedByName, String sharedByEmail,
                            Long sharedWithId, String sharedWithName, String sharedWithEmail,
                            SharePermission permission, LocalDateTime expiresAt,
                            LocalDateTime createdAt, boolean expired) {
        this(id, documentId, documentTitle, sharedById, sharedByName, sharedByEmail,
             sharedWithId, sharedWithName, sharedWithEmail, permission, expiresAt, createdAt, expired, false);
    }

    public DocumentShareDTO(Long id, Long documentId, String documentTitle,
                            Long sharedById, String sharedByName, String sharedByEmail,
                            Long sharedWithId, String sharedWithName, String sharedWithEmail,
                            SharePermission permission, LocalDateTime expiresAt,
                            LocalDateTime createdAt, boolean expired, boolean revoked) {
        this.id = id;
        this.documentId = documentId;
        this.documentTitle = documentTitle;
        this.sharedById = sharedById;
        this.sharedByName = sharedByName;
        this.sharedByEmail = sharedByEmail;
        this.sharedWithId = sharedWithId;
        this.sharedWithName = sharedWithName;
        this.sharedWithEmail = sharedWithEmail;
        this.permission = permission;
        this.expiresAt = expiresAt;
        this.createdAt = createdAt;
        this.expired = expired;
        this.revoked = revoked;
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

    public Long getSharedById() {
        return sharedById;
    }

    public void setSharedById(Long sharedById) {
        this.sharedById = sharedById;
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

    public Long getSharedWithId() {
        return sharedWithId;
    }

    public void setSharedWithId(Long sharedWithId) {
        this.sharedWithId = sharedWithId;
    }

    public String getSharedWithName() {
        return sharedWithName;
    }

    public void setSharedWithName(String sharedWithName) {
        this.sharedWithName = sharedWithName;
    }

    public String getSharedWithEmail() {
        return sharedWithEmail;
    }

    public void setSharedWithEmail(String sharedWithEmail) {
        this.sharedWithEmail = sharedWithEmail;
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

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public boolean isExpired() {
        return expired;
    }

    public void setExpired(boolean expired) {
        this.expired = expired;
    }

    public boolean isRevoked() {
        return revoked;
    }

    public void setRevoked(boolean revoked) {
        this.revoked = revoked;
    }
}
