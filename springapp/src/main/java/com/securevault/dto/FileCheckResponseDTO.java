package com.securevault.dto;

public class FileCheckResponseDTO {

    private String verificationId;
    private boolean matches;
    private Integer matchedVersion;
    private String uploadedFileChecksum;
    private String currentVersionChecksum;
    private String status; // VALID, REVOKED, NOT_FOUND
    private String message;

    public FileCheckResponseDTO() {}

    public FileCheckResponseDTO(String verificationId, boolean matches, Integer matchedVersion,
                                String uploadedFileChecksum, String currentVersionChecksum,
                                String status, String message) {
        this.verificationId = verificationId;
        this.matches = matches;
        this.matchedVersion = matchedVersion;
        this.uploadedFileChecksum = uploadedFileChecksum;
        this.currentVersionChecksum = currentVersionChecksum;
        this.status = status;
        this.message = message;
    }

    public String getVerificationId() {
        return verificationId;
    }

    public void setVerificationId(String verificationId) {
        this.verificationId = verificationId;
    }

    public boolean isMatches() {
        return matches;
    }

    public void setMatches(boolean matches) {
        this.matches = matches;
    }

    public Integer getMatchedVersion() {
        return matchedVersion;
    }

    public void setMatchedVersion(Integer matchedVersion) {
        this.matchedVersion = matchedVersion;
    }

    public String getUploadedFileChecksum() {
        return uploadedFileChecksum;
    }

    public void setUploadedFileChecksum(String uploadedFileChecksum) {
        this.uploadedFileChecksum = uploadedFileChecksum;
    }

    public String getCurrentVersionChecksum() {
        return currentVersionChecksum;
    }

    public void setCurrentVersionChecksum(String currentVersionChecksum) {
        this.currentVersionChecksum = currentVersionChecksum;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
