package com.securevault.dto;

public class MonthlyCountDTO {

    private String month; // e.g. "2026-03"
    private String label; // e.g. "Mar 2026"
    private Long count;

    public MonthlyCountDTO() {
        this.count = 0L;
    }

    public MonthlyCountDTO(String month, String label, Long count) {
        this.month = month;
        this.label = label;
        this.count = count != null ? count : 0L;
    }

    public String getMonth() {
        return month;
    }

    public void setMonth(String month) {
        this.month = month;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public Long getCount() {
        return count;
    }

    public void setCount(Long count) {
        this.count = count;
    }
}
