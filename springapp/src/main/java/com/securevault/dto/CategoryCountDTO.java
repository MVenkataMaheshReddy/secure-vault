package com.securevault.dto;

public class CategoryCountDTO {

    private String category;
    private Long count;

    public CategoryCountDTO() {
        this.count = 0L;
    }

    public CategoryCountDTO(String category, Long count) {
        this.category = category != null ? category : "UNCATEGORIZED";
        this.count = count != null ? count : 0L;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public Long getCount() {
        return count;
    }

    public void setCount(Long count) {
        this.count = count;
    }
}
