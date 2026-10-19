package com.securevault.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.securevault.entity.SharePermission;
import java.time.LocalDateTime;
import javax.validation.constraints.Email;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

public class ShareRequest {

    @NotBlank(message = "Recipient email is required")
    @Email(message = "Invalid recipient email address")
    @JsonAlias({"email", "recipientEmail"})
    private String sharedWithEmail;

    @NotNull(message = "Share permission is required")
    private SharePermission permission = SharePermission.VIEW;

    private LocalDateTime expiresAt;

    public ShareRequest() {}

    public ShareRequest(String sharedWithEmail, SharePermission permission, LocalDateTime expiresAt) {
        this.sharedWithEmail = sharedWithEmail;
        this.permission = permission;
        this.expiresAt = expiresAt;
    }

    public String getSharedWithEmail() {
        return sharedWithEmail;
    }

    public void setSharedWithEmail(String sharedWithEmail) {
        this.sharedWithEmail = sharedWithEmail;
    }

    public String getEmail() {
        return sharedWithEmail;
    }

    public void setEmail(String email) {
        if (email != null && !email.isBlank()) {
            this.sharedWithEmail = email;
        }
    }

    public SharePermission getPermission() {
        return permission;
    }

    public void setPermission(SharePermission permission) {
        this.permission = permission;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(LocalDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }
}
