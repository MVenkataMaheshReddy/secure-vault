package com.securevault.entity;

import java.time.LocalDateTime;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Index;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.PrePersist;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;

@Entity
@Table(
    name = "document_versions",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_doc_version", columnNames = {"document_id", "version_number"})
    },
    indexes = {
        @Index(name = "idx_doc_versions_document_id", columnList = "document_id"),
        @Index(name = "idx_doc_versions_uploaded_by", columnList = "uploaded_by")
    }
)
public class DocumentVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_id", nullable = false)
    private Document document;

    @Column(name = "version_number", nullable = false)
    private Integer versionNumber;

    @Column(name = "file_path", nullable = false, length = 500)
    private String filePath;

    @Column(name = "file_size", nullable = false)
    private Long fileSize;

    @Column(name = "content_type", nullable = false, length = 150)
    private String contentType;

    @Column(name = "checksum", nullable = false, length = 64)
    private String checksum; // SHA-256 hash

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "uploaded_by", nullable = false)
    private User uploadedBy;

    @Column(name = "change_description", length = 255)
    private String changeDescription;

    @Column(name = "ocr_text", columnDefinition = "LONGTEXT")
    private String ocrText;

    @Enumerated(EnumType.STRING)
    @Column(name = "ocr_status", nullable = false, length = 30)
    private OcrStatus ocrStatus = OcrStatus.PENDING;

    @Column(name = "ocr_error_message", length = 1000)
    private String ocrErrorMessage;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
    }

    public DocumentVersion() {}

    public DocumentVersion(Document document, Integer versionNumber, String filePath, Long fileSize,
                           String contentType, String checksum, User uploadedBy, String changeDescription) {
        this.document = document;
        this.versionNumber = versionNumber;
        this.filePath = filePath;
        this.fileSize = fileSize;
        this.contentType = contentType;
        this.checksum = checksum;
        this.uploadedBy = uploadedBy;
        this.changeDescription = changeDescription;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Document getDocument() {
        return document;
    }

    public void setDocument(Document document) {
        this.document = document;
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

    public User getUploadedBy() {
        return uploadedBy;
    }

    public void setUploadedBy(User uploadedBy) {
        this.uploadedBy = uploadedBy;
    }

    public String getChangeDescription() {
        return changeDescription;
    }

    public void setChangeDescription(String changeDescription) {
        this.changeDescription = changeDescription;
    }

    public String getOcrText() {
        return ocrText;
    }

    public void setOcrText(String ocrText) {
        this.ocrText = ocrText;
    }

    public OcrStatus getOcrStatus() {
        return ocrStatus;
    }

    public void setOcrStatus(OcrStatus ocrStatus) {
        this.ocrStatus = ocrStatus;
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
