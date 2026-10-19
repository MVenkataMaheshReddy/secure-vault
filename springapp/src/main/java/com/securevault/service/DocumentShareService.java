package com.securevault.service;

import com.securevault.dto.DocumentShareDTO;
import com.securevault.dto.ShareRequest;
import com.securevault.dto.SharedDocumentDTO;
import com.securevault.entity.Document;
import com.securevault.entity.DocumentShare;
import com.securevault.entity.DocumentVersion;
import com.securevault.entity.User;
import com.securevault.entity.DocumentStatus;
import com.securevault.exception.BadRequestException;
import com.securevault.exception.ConflictException;
import com.securevault.exception.ResourceNotFoundException;
import com.securevault.repository.DocumentRepository;
import com.securevault.repository.DocumentShareRepository;
import com.securevault.repository.DocumentVersionRepository;
import com.securevault.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DocumentShareService {

    private final DocumentShareRepository documentShareRepository;
    private final DocumentRepository documentRepository;
    private final DocumentVersionRepository documentVersionRepository;
    private final UserRepository userRepository;
    private final DocumentAccessService documentAccessService;
    private final AuditService auditService;

    public DocumentShareService(DocumentShareRepository documentShareRepository,
                                DocumentRepository documentRepository,
                                DocumentVersionRepository documentVersionRepository,
                                UserRepository userRepository,
                                DocumentAccessService documentAccessService,
                                AuditService auditService) {
        this.documentShareRepository = documentShareRepository;
        this.documentRepository = documentRepository;
        this.documentVersionRepository = documentVersionRepository;
        this.userRepository = userRepository;
        this.documentAccessService = documentAccessService;
        this.auditService = auditService;
    }

    @Transactional
    public DocumentShareDTO shareDocument(Long documentId, ShareRequest request,
                                          String currentUserEmail, boolean isAdmin) {
        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found with id: " + documentId));

        if (document.isDeleted()) {
            throw new ResourceNotFoundException("Cannot share a deleted document");
        }

        if (!documentAccessService.canShare(document, currentUserEmail, isAdmin)) {
            if (auditService != null && currentUserEmail != null) {
                auditService.log(currentUserEmail, "ACCESS_DENIED", "DOCUMENT_SHARE", documentId,
                        "Unauthorized attempt to share document " + documentId);
            }
            throw new AccessDeniedException("You do not have permission to share this document");
        }

        // Only APPROVED documents can be shared (409 otherwise)
        if (document.getStatus() != DocumentStatus.APPROVED) {
            throw new ConflictException("Only APPROVED documents can be shared");
        }

        String recipientEmail = request.getSharedWithEmail() != null ? request.getSharedWithEmail().trim() : "";
        if (recipientEmail.isEmpty()) {
            throw new BadRequestException("Recipient email is required");
        }

        // Validate self-sharing
        if (currentUserEmail != null && recipientEmail.equalsIgnoreCase(currentUserEmail.trim())) {
            throw new BadRequestException("Cannot share document with yourself");
        }
        if (document.getOwner() != null && document.getOwner().getEmail() != null &&
                recipientEmail.equalsIgnoreCase(document.getOwner().getEmail().trim())) {
            throw new BadRequestException("Cannot share document with the document owner");
        }

        User sender = userRepository.findByEmail(currentUserEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Current user not found"));

        // The recipient must be an existing, enabled user (404 "User not found")
        User recipient = userRepository.findByEmail(recipientEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (!recipient.isEnabled()) {
            throw new ResourceNotFoundException("User not found");
        }

        // Check if share already exists for this document and recipient (duplicate share updates existing and un-revokes it)
        Optional<DocumentShare> existingOpt = documentShareRepository
                .findByDocumentIdAndSharedWithId(documentId, recipient.getId());

        DocumentShare share;
        if (existingOpt.isPresent()) {
            share = existingOpt.get();
            share.setPermission(request.getPermission());
            share.setExpiresAt(request.getExpiresAt());
            share.setSharedBy(sender);
            share.setRevoked(false);
        } else {
            share = new DocumentShare(document, sender, recipient,
                    request.getPermission(), request.getExpiresAt());
            share.setRevoked(false);
        }

        DocumentShare saved = documentShareRepository.save(share);

        // Audit log
        if (auditService != null) {
            auditService.log(sender, "SHARE_CREATE", "DOCUMENT_SHARE", saved.getId(),
                    "Shared document '" + document.getTitle() + "' with " + recipient.getEmail() +
                    " (Permission: " + request.getPermission() +
                    (request.getExpiresAt() != null ? ", Expires: " + request.getExpiresAt() : "") + ")");
        }

        return mapToDTO(saved);
    }

    @Transactional(readOnly = true)
    public List<DocumentShareDTO> getDocumentShares(Long documentId, String currentUserEmail, boolean isAdmin) {
        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found with id: " + documentId));

        if (document.isDeleted()) {
            throw new ResourceNotFoundException("Document is deleted");
        }

        if (!documentAccessService.canShare(document, currentUserEmail, isAdmin)) {
            if (auditService != null && currentUserEmail != null) {
                auditService.log(currentUserEmail, "ACCESS_DENIED", "DOCUMENT_SHARE", documentId,
                        "Unauthorized attempt to list shares for document " + documentId);
            }
            throw new AccessDeniedException("You do not have permission to view shares for this document");
        }

        return documentShareRepository.findByDocumentIdAndRevokedFalseOrderByCreatedAtDesc(documentId)
                .stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    @Transactional
    public void revokeShare(Long documentId, Long shareId, String currentUserEmail, boolean isAdmin) {
        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found with id: " + documentId));

        if (!documentAccessService.canShare(document, currentUserEmail, isAdmin)) {
            if (auditService != null && currentUserEmail != null) {
                auditService.log(currentUserEmail, "ACCESS_DENIED", "DOCUMENT_SHARE", documentId,
                        "Unauthorized attempt to revoke share on document " + documentId);
            }
            throw new AccessDeniedException("You do not have permission to revoke shares for this document");
        }

        DocumentShare share = documentShareRepository.findById(shareId)
                .orElseThrow(() -> new ResourceNotFoundException("Share record not found with id: " + shareId));

        if (!share.getDocument().getId().equals(documentId)) {
            throw new BadRequestException("Share record does not belong to this document");
        }

        share.setRevoked(true);
        documentShareRepository.save(share);

        String recipientEmail = share.getSharedWith() != null ? share.getSharedWith().getEmail() : "unknown";

        // Audit log
        if (auditService != null) {
            auditService.log(currentUserEmail, "SHARE_REVOKE", "DOCUMENT_SHARE", shareId,
                    "Revoked share on document '" + document.getTitle() + "' for user " + recipientEmail);
        }
    }

    @Transactional(readOnly = true)
    public Page<SharedDocumentDTO> getDocumentsSharedWithMe(String currentUserEmail, Pageable pageable) {
        User recipient = userRepository.findByEmail(currentUserEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with email: " + currentUserEmail));

        Page<DocumentShare> sharesPage = documentShareRepository.findActiveSharesForUser(
                recipient.getId(), LocalDateTime.now(), pageable);

        return sharesPage.map(this::mapToSharedDocDTO);
    }

    public DocumentShareDTO mapToDTO(DocumentShare share) {
        boolean isExpired = share.getExpiresAt() != null && share.getExpiresAt().isBefore(LocalDateTime.now());
        return new DocumentShareDTO(
                share.getId(),
                share.getDocument() != null ? share.getDocument().getId() : null,
                share.getDocument() != null ? share.getDocument().getTitle() : null,
                share.getSharedBy() != null ? share.getSharedBy().getId() : null,
                share.getSharedBy() != null ? share.getSharedBy().getName() : null,
                share.getSharedBy() != null ? share.getSharedBy().getEmail() : null,
                share.getSharedWith() != null ? share.getSharedWith().getId() : null,
                share.getSharedWith() != null ? share.getSharedWith().getName() : null,
                share.getSharedWith() != null ? share.getSharedWith().getEmail() : null,
                share.getPermission(),
                share.getExpiresAt(),
                share.getCreatedAt(),
                isExpired,
                share.isRevoked()
        );
    }

    public SharedDocumentDTO mapToSharedDocDTO(DocumentShare share) {
        Document doc = share.getDocument();
        DocumentVersion currentVersion = null;
        if (doc != null && doc.getCurrentVersion() != null) {
            currentVersion = documentVersionRepository
                    .findByDocumentIdAndVersionNumber(doc.getId(), doc.getCurrentVersion())
                    .orElse(null);
        }

        return new SharedDocumentDTO(
                share.getId(),
                doc != null ? doc.getId() : null,
                doc != null ? doc.getTitle() : null,
                doc != null ? doc.getCategory() : null,
                doc != null ? doc.getDescription() : null,
                doc != null ? doc.getOriginalFileName() : null,
                doc != null ? doc.getCurrentVersion() : 1,
                doc != null && doc.getStatus() != null ? doc.getStatus().name() : "ACTIVE",
                share.getSharedBy() != null ? share.getSharedBy().getName() : null,
                share.getSharedBy() != null ? share.getSharedBy().getEmail() : null,
                share.getPermission(),
                share.getExpiresAt(),
                share.getCreatedAt(),
                currentVersion != null ? currentVersion.getFileSize() : null,
                currentVersion != null ? currentVersion.getContentType() : null
        );
    }
}
