package com.securevault.service;

import com.securevault.entity.Document;
import com.securevault.entity.DocumentShare;
import com.securevault.entity.DocumentStatus;
import com.securevault.entity.SharePermission;
import com.securevault.entity.User;
import com.securevault.repository.DocumentShareRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class DocumentAccessService {

    private final DocumentShareRepository documentShareRepository;

    public DocumentAccessService() {
        this.documentShareRepository = null;
    }

    @Autowired
    public DocumentAccessService(DocumentShareRepository documentShareRepository) {
        this.documentShareRepository = documentShareRepository;
    }

    public boolean canView(Document document, String userEmail, boolean isAdmin) {
        return canView(document, userEmail, isAdmin, false);
    }

    public boolean canView(Document document, String userEmail, boolean isAdmin, boolean isReviewer) {
        if (isAdmin) return true;
        if (document == null || userEmail == null) return false;
        if (isOwner(document, userEmail)) return true;
        if (isReviewer && document.getStatus() == DocumentStatus.PENDING) return true;

        // Phase 9: Active share check (VIEW or DOWNLOAD permission)
        return hasActiveShare(document.getId(), userEmail, null);
    }

    public boolean canDownload(Document document, String userEmail, boolean isAdmin) {
        return canDownload(document, userEmail, isAdmin, false);
    }

    public boolean canDownload(Document document, String userEmail, boolean isAdmin, boolean isReviewer) {
        if (isAdmin) return true;
        if (document == null || userEmail == null) return false;
        if (isOwner(document, userEmail)) return true;
        if (isReviewer && document.getStatus() == DocumentStatus.PENDING) return true;

        // Phase 9: Active share check with DOWNLOAD permission specifically
        return hasActiveShare(document.getId(), userEmail, SharePermission.DOWNLOAD);
    }

    public boolean canShare(Document document, String userEmail, boolean isAdmin) {
        if (isAdmin) return true;
        if (document == null || userEmail == null) return false;
        return isOwner(document, userEmail);
    }

    public boolean canEdit(Document document, String userEmail, boolean isAdmin) {
        if (isAdmin) return true;
        if (document == null || userEmail == null) return false;
        return isOwner(document, userEmail);
    }

    public boolean canDelete(Document document, String userEmail, boolean isAdmin) {
        if (isAdmin) return true;
        if (document == null || userEmail == null) return false;
        return isOwner(document, userEmail);
    }

    public boolean canRestore(Document document, String userEmail, boolean isAdmin) {
        if (isAdmin) return true;
        if (document == null || userEmail == null) return false;
        return isOwner(document, userEmail);
    }

    public boolean canView(Document document, User user) {
        if (user == null || document == null) return false;
        boolean isAdmin = user.getRoles() != null && user.getRoles().stream()
                .anyMatch(r -> r.getName().name().equals("ADMIN"));
        boolean isReviewer = user.getRoles() != null && user.getRoles().stream()
                .anyMatch(r -> r.getName().name().equals("REVIEWER"));
        return canView(document, user.getEmail(), isAdmin, isReviewer);
    }

    public boolean canDownload(Document document, User user) {
        if (user == null || document == null) return false;
        boolean isAdmin = user.getRoles() != null && user.getRoles().stream()
                .anyMatch(r -> r.getName().name().equals("ADMIN"));
        boolean isReviewer = user.getRoles() != null && user.getRoles().stream()
                .anyMatch(r -> r.getName().name().equals("REVIEWER"));
        return canDownload(document, user.getEmail(), isAdmin, isReviewer);
    }

    public boolean canEdit(Document document, User user) {
        if (user == null || document == null) return false;
        boolean isAdmin = user.getRoles() != null && user.getRoles().stream()
                .anyMatch(r -> r.getName().name().equals("ADMIN"));
        return canEdit(document, user.getEmail(), isAdmin);
    }

    public boolean canDelete(Document document, User user) {
        if (user == null || document == null) return false;
        boolean isAdmin = user.getRoles() != null && user.getRoles().stream()
                .anyMatch(r -> r.getName().name().equals("ADMIN"));
        return canDelete(document, user.getEmail(), isAdmin);
    }

    public boolean hasActiveShare(Long documentId, String userEmail, SharePermission requiredPermission) {
        if (documentShareRepository == null || documentId == null || userEmail == null) {
            return false;
        }
        Optional<DocumentShare> shareOpt = documentShareRepository.findByDocumentIdAndSharedWithEmail(
                documentId, userEmail.trim().toLowerCase());
        if (shareOpt.isEmpty()) {
            return false;
        }
        DocumentShare share = shareOpt.get();

        // Immediate revocation check
        if (share.isRevoked()) {
            return false;
        }

        // Expired shares are ignored (evaluated at access time)
        if (share.getExpiresAt() != null && share.getExpiresAt().isBefore(LocalDateTime.now())) {
            return false;
        }

        // If DOWNLOAD permission is required, verify share has DOWNLOAD
        if (requiredPermission == SharePermission.DOWNLOAD && share.getPermission() != SharePermission.DOWNLOAD) {
            return false;
        }

        return true;
    }

    private boolean isOwner(Document document, String userEmail) {
        return document.getOwner() != null &&
               document.getOwner().getEmail() != null &&
               document.getOwner().getEmail().equalsIgnoreCase(userEmail);
    }
}
