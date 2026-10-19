package com.securevault.entity;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.persistence.CascadeType;
import javax.persistence.CollectionTable;
import javax.persistence.Column;
import javax.persistence.ElementCollection;
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
import javax.persistence.OneToMany;
import javax.persistence.Table;

@Entity
@Table(
    name = "documents",
    indexes = {
        @Index(name = "idx_documents_owner", columnList = "owner_id"),
        @Index(name = "idx_documents_status", columnList = "status"),
        @Index(name = "idx_documents_verification_id", columnList = "verification_id", unique = true)
    }
)
public class Document extends BaseAuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Column(name = "original_file_name", nullable = false, length = 255)
    private String originalFileName;

    @Column(name = "category", length = 100)
    private String category;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private DocumentStatus status = DocumentStatus.ACTIVE;

    @Column(name = "current_version", nullable = false)
    private Integer currentVersion = 1;

    @Column(name = "verification_id", unique = true, length = 100)
    private String verificationId;

    @Column(name = "deleted", nullable = false)
    private boolean deleted = false;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @OneToMany(mappedBy = "document", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<DocumentVersion> versions = new ArrayList<>();

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "document_tags", joinColumns = @JoinColumn(name = "document_id"))
    @Column(name = "tag", length = 50)
    private Set<String> tags = new HashSet<>();

    public Document() {}

    public Document(String title, String originalFileName, String category, String description, User owner) {
        this.title = title;
        this.originalFileName = originalFileName;
        this.category = category;
        this.description = description;
        this.owner = owner;
        this.status = DocumentStatus.ACTIVE;
        this.currentVersion = 1;
        this.deleted = false;
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

    public User getOwner() {
        return owner;
    }

    public void setOwner(User owner) {
        this.owner = owner;
    }

    public DocumentStatus getStatus() {
        return status;
    }

    public void setStatus(DocumentStatus status) {
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

    public List<DocumentVersion> getVersions() {
        return versions;
    }

    public void setVersions(List<DocumentVersion> versions) {
        this.versions = versions;
    }

    public void addVersion(DocumentVersion version) {
        versions.add(version);
        version.setDocument(this);
    }

    public Set<String> getTags() {
        return tags;
    }

    public void setTags(Set<String> tags) {
        this.tags = tags;
    }

    // Compatibility getters & setters for legacy frontend (doc_id, doc_name, user)
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

    public User getUser() {
        return owner;
    }

    public void setUser(User user) {
        this.owner = user;
    }
}
