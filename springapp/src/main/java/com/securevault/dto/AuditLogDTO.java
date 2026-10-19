package com.securevault.dto;

import java.time.LocalDateTime;

public class AuditLogDTO {

    private Long id;
    private Long userId;
    private String userName;
    private String userEmail;
    private String action;
    private String entityType;
    private Long entityId;
    private String ipAddress;
    private String details;
    private LocalDateTime timestamp;

    public AuditLogDTO() {}

    public AuditLogDTO(Long id, Long userId, String userName, String userEmail,
                       String action, String entityType, Long entityId,
                       String ipAddress, String details, LocalDateTime timestamp) {
        this.id = id;
        this.userId = userId;
        this.userName = userName;
        this.userEmail = userEmail;
        this.action = action;
        this.entityType = entityType;
        this.entityId = entityId;
        this.ipAddress = ipAddress;
        this.details = details;
        this.timestamp = timestamp;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public String getUserEmail() {
        return userEmail;
    }

    public void setUserEmail(String userEmail) {
        this.userEmail = userEmail;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getEntityType() {
        return entityType;
    }

    public void setEntityType(String entityType) {
        this.entityType = entityType;
    }

    public Long getEntityId() {
        return entityId;
    }

    public void setEntityId(Long entityId) {
        this.entityId = entityId;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public String getDetails() {
        return details;
    }

    public void setDetails(String details) {
        this.details = details;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }
}
