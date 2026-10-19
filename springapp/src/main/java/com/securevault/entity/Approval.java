package com.securevault.entity;

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
import javax.persistence.Table;

@Entity
@Table(
    name = "approvals",
    indexes = {
        @Index(name = "idx_approvals_document_id", columnList = "document_id"),
        @Index(name = "idx_approvals_reviewer_id", columnList = "reviewer_id")
    }
)
public class Approval extends BaseAuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_id", nullable = false)
    private Document document;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewer_id", nullable = true)
    private User reviewer;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private ApprovalStatus status = ApprovalStatus.PENDING;

    @Column(name = "comments", columnDefinition = "TEXT")
    private String comments;

    public Approval() {}

    public Approval(Document document, ApprovalStatus status) {
        this.document = document;
        this.status = status;
    }

    public Approval(Document document, ApprovalStatus status, String comments) {
        this.document = document;
        this.status = status;
        this.comments = comments;
    }

    public Approval(Document document, User reviewer, ApprovalStatus status, String comments) {
        this.document = document;
        this.reviewer = reviewer;
        this.status = status;
        this.comments = comments;
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

    public User getReviewer() {
        return reviewer;
    }

    public void setReviewer(User reviewer) {
        this.reviewer = reviewer;
    }

    public ApprovalStatus getStatus() {
        return status;
    }

    public void setStatus(ApprovalStatus status) {
        this.status = status;
    }

    public String getComments() {
        return comments;
    }

    public void setComments(String comments) {
        this.comments = comments;
    }
}
