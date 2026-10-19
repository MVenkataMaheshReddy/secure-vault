package com.securevault.dto;

import java.util.Set;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

public class UpdateDocumentRequest {

    @NotBlank(message = "Title is required")
    @Size(max = 255, message = "Title cannot exceed 255 characters")
    private String title;

    @Size(max = 100, message = "Category cannot exceed 100 characters")
    private String category;

    @Size(max = 1000, message = "Description cannot exceed 1000 characters")
    private String description;
    private Set<String> tags;

    public UpdateDocumentRequest() {}

    public UpdateDocumentRequest(String title, String category, String description, Set<String> tags) {
        this.title = title;
        this.category = category;
        this.description = description;
        this.tags = tags;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
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

    public Set<String> getTags() {
        return tags;
    }

    public void setTags(Set<String> tags) {
        this.tags = tags;
    }
}
