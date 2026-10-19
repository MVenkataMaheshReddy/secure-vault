package com.securevault.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;

public class PublicVerificationDTO {

    private String verificationId;
    private String status; // VALID, REVOKED
    private String documentTitle;
    private String category;
    private String uploadedBy; // display name only (no email)

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime approvedAt;

    private Integer currentVersion;
    private String checksum;

    public PublicVerificationDTO() {}

    public PublicVerificationDTO(String verificationId, String documentTitle, String category,
                                 String uploadedBy, String status, LocalDateTime approvedAt,
                                 Integer currentVersion, String checksum) {
        this.verificationId = verificationId;
        this.documentTitle = documentTitle;
        this.category = category;
        this.uploadedBy = uploadedBy;
        this.status = status;
        this.approvedAt = approvedAt;
        this.currentVersion = currentVersion;
        this.checksum = checksum;
    }

    public String getVerificationId() {
        return verificationId;
    }

    public void setVerificationId(String verificationId) {
        this.verificationId = verificationId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
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

    public String getUploadedBy() {
        return uploadedBy;
    }

    public void setUploadedBy(String uploadedBy) {
        this.uploadedBy = uploadedBy;
    }

    @JsonIgnore
    public String getOwnerDisplayName() {
        return uploadedBy;
    }

    @JsonProperty("ownerDisplayName")
    public void setOwnerDisplayName(String ownerDisplayName) {
        if (this.uploadedBy == null) {
            this.uploadedBy = ownerDisplayName;
        }
    }

    public LocalDateTime getApprovedAt() {
        return approvedAt;
    }

    public void setApprovedAt(LocalDateTime approvedAt) {
        this.approvedAt = approvedAt;
    }

    @JsonIgnore
    public LocalDateTime getIssuedDate() {
        return approvedAt;
    }

    @JsonProperty("issuedDate")
    public void setIssuedDate(LocalDateTime issuedDate) {
        if (this.approvedAt == null) {
            this.approvedAt = issuedDate;
        }
    }

    public Integer getCurrentVersion() {
        return currentVersion;
    }

    public void setCurrentVersion(Integer currentVersion) {
        this.currentVersion = currentVersion;
    }

    @JsonIgnore
    public Integer getCurrentVersionNumber() {
        return currentVersion;
    }

    @JsonProperty("currentVersionNumber")
    public void setCurrentVersionNumber(Integer currentVersionNumber) {
        if (this.currentVersion == null) {
            this.currentVersion = currentVersionNumber;
        }
    }

    public String getChecksum() {
        return checksum;
    }

    public void setChecksum(String checksum) {
        this.checksum = checksum;
    }
}
