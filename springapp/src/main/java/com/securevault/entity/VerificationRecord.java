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

@Entity
@Table(
    name = "verification_records",
    indexes = {
        @Index(name = "idx_verification_id", columnList = "verification_id", unique = true),
        @Index(name = "idx_verification_doc_id", columnList = "document_id")
    }
)
public class VerificationRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_id", nullable = false)
    private Document document;

    @Column(name = "verification_id", nullable = false, unique = true, length = 100)
    private String verificationId;

    @Column(name = "qr_path", length = 500)
    private String qrPath;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private VerificationStatus status = VerificationStatus.VALID;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
    }

    public VerificationRecord() {}

    public VerificationRecord(Document document, String verificationId, String qrPath, VerificationStatus status) {
        this.document = document;
        this.verificationId = verificationId;
        this.qrPath = qrPath;
        this.status = status;
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

    public String getVerificationId() {
        return verificationId;
    }

    public void setVerificationId(String verificationId) {
        this.verificationId = verificationId;
    }

    public String getQrPath() {
        return qrPath;
    }

    public void setQrPath(String qrPath) {
        this.qrPath = qrPath;
    }

    public VerificationStatus getStatus() {
        return status;
    }

    public void setStatus(VerificationStatus status) {
        this.status = status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
