package com.securevault.dto;

import java.time.LocalDateTime;

public class ActivityLogDTO {

    private Long act_id;
    private Long userId;
    private Long documentId;
    private String action;
    private LocalDateTime timestamp;

    public ActivityLogDTO() {}

    public ActivityLogDTO(Long act_id, Long userId, Long documentId, String action, LocalDateTime timestamp) {
        this.act_id = act_id;
        this.userId = userId;
        this.documentId = documentId;
        this.action = action;
        this.timestamp = timestamp;
    }

    public Long getAct_id() {
        return act_id;
    }

    public void setAct_id(Long act_id) {
        this.act_id = act_id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Long getDocumentId() {
        return documentId;
    }

    public void setDocumentId(Long documentId) {
        this.documentId = documentId;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }
}
