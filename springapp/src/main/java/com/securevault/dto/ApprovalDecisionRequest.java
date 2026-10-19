package com.securevault.dto;

import javax.validation.constraints.Size;

public class ApprovalDecisionRequest {

    @Size(max = 1000, message = "Comments cannot exceed 1000 characters")
    private String comments;

    public ApprovalDecisionRequest() {}

    public ApprovalDecisionRequest(String comments) {
        this.comments = comments;
    }

    public String getComments() {
        return comments;
    }

    public void setComments(String comments) {
        this.comments = comments;
    }
}
