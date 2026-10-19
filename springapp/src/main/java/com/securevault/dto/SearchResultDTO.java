package com.securevault.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.securevault.entity.OcrStatus;
import java.time.LocalDateTime;
import java.util.Set;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class SearchResultDTO {

    private Long id;
    private String title;
    private String originalFileName;
    private String category;
    private String status;
    private Integer currentVersion;
    private Long fileSize;
    private String contentType;
    private String snippet;
    private String matchSource; // "content" or "metadata"
    private OcrStatus ocrStatus;
    private String ownerEmail;
    private String ownerName;
    private Set<String> tags;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public SearchResultDTO() {}

    public SearchResultDTO(Long id, String title, String originalFileName, String category,
                           String status, Integer currentVersion, Long fileSize,
                           String contentType, String snippet, String matchSource,
                           OcrStatus ocrStatus, String ownerEmail, String ownerName,
                           Set<String> tags, LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.title = title;
        this.originalFileName = originalFileName;
        this.category = category;
        this.status = status;
        this.currentVersion = currentVersion;
        this.fileSize = fileSize;
        this.contentType = contentType;
        this.snippet = snippet;
        this.matchSource = matchSource;
        this.ocrStatus = ocrStatus;
        this.ownerEmail = ownerEmail;
        this.ownerName = ownerName;
        this.tags = tags;
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

    public String getSnippet() {
        return snippet;
    }

    public void setSnippet(String snippet) {
        this.snippet = snippet;
    }

    public String getMatchSource() {
        return matchSource;
    }

    public void setMatchSource(String matchSource) {
        this.matchSource = matchSource;
    }

    public OcrStatus getOcrStatus() {
        return ocrStatus;
    }

    public void setOcrStatus(OcrStatus ocrStatus) {
        this.ocrStatus = ocrStatus;
    }

    public String getOwnerEmail() {
        return ownerEmail;
    }

    public void setOwnerEmail(String ownerEmail) {
        this.ownerEmail = ownerEmail;
    }

    public String getOwnerName() {
        return ownerName;
    }

    public void setOwnerName(String ownerName) {
        this.ownerName = ownerName;
    }

    public Set<String> getTags() {
        return tags;
    }

    public void setTags(Set<String> tags) {
        this.tags = tags;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
