package com.securevault.dto;

public class MonthlyApprovalDTO {

    private String month; // e.g. "2026-03"
    private String label; // e.g. "Mar 2026"
    private Long approved;
    private Long rejected;

    public MonthlyApprovalDTO() {
        this.approved = 0L;
        this.rejected = 0L;
    }

    public MonthlyApprovalDTO(String month, String label, Long approved, Long rejected) {
        this.month = month;
        this.label = label;
        this.approved = approved != null ? approved : 0L;
        this.rejected = rejected != null ? rejected : 0L;
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

    public Long getApproved() {
        return approved;
    }

    public void setApproved(Long approved) {
        this.approved = approved;
    }

    public Long getRejected() {
        return rejected;
    }

    public void setRejected(Long rejected) {
        this.rejected = rejected;
    }
}
