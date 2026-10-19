package com.securevault.dto;

import java.time.LocalDate;
import javax.validation.constraints.Size;

public class FolderDTO {

    private Long fol_id;

    @Size(max = 100, message = "Folder name cannot exceed 100 characters")
    private String fol_name;

    private Long ownerId;
    private LocalDate createdAt;

    @Size(max = 255, message = "Folder description cannot exceed 255 characters")
    private String description;

    public FolderDTO() {}

    public FolderDTO(Long fol_id, String fol_name, Long ownerId, LocalDate createdAt, String description) {
        this.fol_id = fol_id;
        this.fol_name = fol_name;
        this.ownerId = ownerId;
        this.createdAt = createdAt;
        this.description = description;
    }

    public Long getFol_id() {
        return fol_id;
    }

    public void setFol_id(Long fol_id) {
        this.fol_id = fol_id;
    }

    public String getFol_name() {
        return fol_name;
    }

    public void setFol_name(String fol_name) {
        this.fol_name = fol_name;
    }

    public Long getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(Long ownerId) {
        this.ownerId = ownerId;
    }

    public LocalDate getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDate createdAt) {
        this.createdAt = createdAt;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
