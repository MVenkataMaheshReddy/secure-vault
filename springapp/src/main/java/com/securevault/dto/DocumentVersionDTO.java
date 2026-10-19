package com.securevault.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDateTime;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class DocumentVersionDTO {

    private Long id;
    private Long documentId;
    private Integer versionNumber;
    private String filePath;
    private Long fileSize;
    private String contentType;
    private String checksum;
    private Long uploadedById;
    private String uploadedByName;
    private String uploadedByEmail;
    private String changeDescription;
    private boolean isCurrent;
    private com.securevault.entity.OcrStatus ocrStatus;
    private String ocrText;
    private String ocrErrorMessage;
    private LocalDateTime createdAt;

    public DocumentVersionDTO() {}

    public DocumentVersionDTO(Long id, Long documentId, Integer versionNumber, String filePath,
                              Long fileSize, String contentType, String checksum,
                              Long uploadedById, String uploadedByName, String uploadedByEmail,
                              String changeDescription, boolean isCurrent, LocalDateTime createdAt) {
        this.id = id;
        this.documentId = documentId;
        this.versionNumber = versionNumber;
        this.filePath = filePath;
        this.fileSize = fileSize;
        this.contentType = contentType;
        this.checksum = checksum;
        this.uploadedById = uploadedById;
        this.uploadedByName = uploadedByName;
        this.uploadedByEmail = uploadedByEmail;
        this.changeDescription = changeDescription;
        this.isCurrent = isCurrent;
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

    public Integer getVersionNumber() {
        return versionNumber;
    }

    public void setVersionNumber(Integer versionNumber) {
        this.versionNumber = versionNumber;
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
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

    public Long getUploadedById() {
        return uploadedById;
    }

    public void setUploadedById(Long uploadedById) {
        this.uploadedById = uploadedById;
    }

    public String getUploadedByName() {
        return uploadedByName;
    }

    public void setUploadedByName(String uploadedByName) {
        this.uploadedByName = uploadedByName;
    }

    public String getUploadedByEmail() {
        return uploadedByEmail;
    }

    public void setUploadedByEmail(String uploadedByEmail) {
        this.uploadedByEmail = uploadedByEmail;
    }

    public String getChangeDescription() {
        return changeDescription;
    }

    public void setChangeDescription(String changeDescription) {
        this.changeDescription = changeDescription;
    }

    public boolean isCurrent() {
        return isCurrent;
    }

    public void setCurrent(boolean current) {
        isCurrent = current;
    }

    public com.securevault.entity.OcrStatus getOcrStatus() {
        return ocrStatus;
    }

    public void setOcrStatus(com.securevault.entity.OcrStatus ocrStatus) {
        this.ocrStatus = ocrStatus;
    }

    public String getOcrText() {
        return ocrText;
    }

    public void setOcrText(String ocrText) {
        this.ocrText = ocrText;
    }

    public String getOcrErrorMessage() {
        return ocrErrorMessage;
    }

    public void setOcrErrorMessage(String ocrErrorMessage) {
        this.ocrErrorMessage = ocrErrorMessage;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
