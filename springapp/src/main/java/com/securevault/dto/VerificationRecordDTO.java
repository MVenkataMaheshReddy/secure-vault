package com.securevault.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.securevault.entity.VerificationStatus;
import java.time.LocalDateTime;

public class VerificationRecordDTO {

    private Long id;
    private Long documentId;
    private String documentTitle;
    private String category;
    private String verificationId;
    private VerificationStatus status;
    private Integer currentVersion;
    private String ownerName;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime createdAt;

    public VerificationRecordDTO() {}

    public VerificationRecordDTO(Long id, Long documentId, String documentTitle, String category,
                                 String verificationId, VerificationStatus status,
                                 Integer currentVersion, String ownerName, LocalDateTime createdAt) {
        this.id = id;
        this.documentId = documentId;
        this.documentTitle = documentTitle;
        this.category = category;
        this.verificationId = verificationId;
        this.status = status;
        this.currentVersion = currentVersion;
        this.ownerName = ownerName;
        this.createdAt = createdAt;
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

    public String getVerificationId() {
        return verificationId;
    }

    public void setVerificationId(String verificationId) {
        this.verificationId = verificationId;
    }

    public VerificationStatus getStatus() {
        return status;
    }

    public void setStatus(VerificationStatus status) {
        this.status = status;
    }

    public Integer getCurrentVersion() {
        return currentVersion;
    }

    public void setCurrentVersion(Integer currentVersion) {
        this.currentVersion = currentVersion;
    }

    public String getOwnerName() {
        return ownerName;
    }

    public void setOwnerName(String ownerName) {
        this.ownerName = ownerName;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
