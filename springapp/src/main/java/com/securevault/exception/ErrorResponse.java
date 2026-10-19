package com.securevault.exception;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalDateTime;

public class ErrorResponse {

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime timestamp;
    private int status;
    private String error;
    private String message;
    private String path;
    private String correlationId;

    public ErrorResponse() {
        this.timestamp = LocalDateTime.now();
        this.correlationId = java.util.UUID.randomUUID().toString();
    }

    public ErrorResponse(int status, String error, String message, String path) {
        this(status, error, message, path, java.util.UUID.randomUUID().toString());
    }

    public ErrorResponse(int status, String error, String message, String path, String correlationId) {
        this.timestamp = LocalDateTime.now();
        this.status = status;
        this.error = error;
        this.message = message;
        this.path = path;
        this.correlationId = (correlationId != null && !correlationId.isBlank())
                ? correlationId
                : java.util.UUID.randomUUID().toString();
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public void setCorrelationId(String correlationId) {
        this.correlationId = correlationId;
    }
}
