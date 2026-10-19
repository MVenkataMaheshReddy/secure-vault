package com.securevault.service;

import com.securevault.dto.DocumentDTO;
import com.securevault.dto.DocumentVersionDTO;
import com.securevault.dto.PageResponse;
import com.securevault.dto.SearchResultDTO;
import com.securevault.dto.UpdateDocumentRequest;
import com.securevault.entity.Document;
import com.securevault.entity.DocumentStatus;
import com.securevault.entity.DocumentVersion;
import com.securevault.entity.OcrStatus;
import com.securevault.entity.Role;
import com.securevault.entity.RoleName;
import com.securevault.entity.User;
import com.securevault.exception.BadRequestException;
import com.securevault.exception.ConflictException;
import com.securevault.exception.ForbiddenException;
import com.securevault.exception.ResourceNotFoundException;
import com.securevault.repository.DocumentRepository;
import com.securevault.repository.DocumentVersionRepository;
import com.securevault.repository.UserRepository;
import com.securevault.service.ocr.OcrProcessingService;
import com.securevault.util.FileValidationUtil;
import com.securevault.util.SnippetUtil;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

@Service
public class DocumentService {

    private final DocumentRepository documentRepository;
    private final DocumentVersionRepository documentVersionRepository;
    private final UserRepository userRepository;
    private final FileStorageService fileStorageService;
    private final DocumentAccessService documentAccessService;
    private final FileValidationUtil fileValidationUtil;
    private OcrProcessingService ocrProcessingService;
    private com.securevault.repository.ApprovalRepository approvalRepository;
    private VerificationService verificationService;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void setVerificationService(VerificationService verificationService) {
        this.verificationService = verificationService;
    }

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private javax.sql.DataSource dataSource;
    private Boolean isMySql = null;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void setDataSource(javax.sql.DataSource dataSource) {
        this.dataSource = dataSource;
    }

    private boolean isMySql() {
        if (isMySql != null) return isMySql;
        synchronized (this) {
            if (isMySql != null) return isMySql;
            if (dataSource == null) {
                isMySql = false;
                return false;
            }
            try (java.sql.Connection conn = dataSource.getConnection()) {
                String productName = conn.getMetaData().getDatabaseProductName();
                isMySql = productName != null && productName.toLowerCase().contains("mysql") && !productName.toLowerCase().contains("h2");
            } catch (Exception e) {
                isMySql = false;
            }
            return Boolean.TRUE.equals(isMySql);
        }
    }

    @org.springframework.beans.factory.annotation.Autowired
    public DocumentService(DocumentRepository documentRepository,
                           DocumentVersionRepository documentVersionRepository,
                           UserRepository userRepository,
                           FileStorageService fileStorageService,
                           DocumentAccessService documentAccessService,
                           FileValidationUtil fileValidationUtil,
                           @org.springframework.beans.factory.annotation.Autowired(required = false) OcrProcessingService ocrProcessingService,
                           @org.springframework.beans.factory.annotation.Autowired(required = false) AuditService auditService) {
        this.documentRepository = documentRepository;
        this.documentVersionRepository = documentVersionRepository;
        this.userRepository = userRepository;
        this.fileStorageService = fileStorageService;
        this.documentAccessService = documentAccessService;
        this.fileValidationUtil = fileValidationUtil != null ? fileValidationUtil : new FileValidationUtil();
        this.ocrProcessingService = ocrProcessingService;
        this.auditService = auditService;
    }

    public DocumentService(DocumentRepository documentRepository,
                           DocumentVersionRepository documentVersionRepository,
                           UserRepository userRepository,
                           FileStorageService fileStorageService,
                           DocumentAccessService documentAccessService,
                           FileValidationUtil fileValidationUtil,
                           OcrProcessingService ocrProcessingService) {
        this(documentRepository, documentVersionRepository, userRepository, fileStorageService, documentAccessService, fileValidationUtil, ocrProcessingService, null);
    }

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void setApprovalRepository(com.securevault.repository.ApprovalRepository approvalRepository) {
        this.approvalRepository = approvalRepository;
    }

    public DocumentService(DocumentRepository documentRepository,
                           DocumentVersionRepository documentVersionRepository,
                           UserRepository userRepository,
                           FileStorageService fileStorageService,
                           DocumentAccessService documentAccessService,
                           FileValidationUtil fileValidationUtil) {
        this(documentRepository, documentVersionRepository, userRepository, fileStorageService, documentAccessService, fileValidationUtil, null);
    }

    public DocumentService(DocumentRepository documentRepository,
                           DocumentVersionRepository documentVersionRepository,
                           UserRepository userRepository,
                           FileStorageService fileStorageService,
                           DocumentAccessService documentAccessService) {
        this(documentRepository, documentVersionRepository, userRepository, fileStorageService, documentAccessService, new FileValidationUtil(), null);
    }

    @Autowired(required = false)
    public void setOcrProcessingService(OcrProcessingService ocrProcessingService) {
        this.ocrProcessingService = ocrProcessingService;
    }

    private AuditService auditService;

    @Autowired(required = false)
    public void setAuditService(AuditService auditService) {
        this.auditService = auditService;
    }

    private void logAccessDenied(String currentUserEmail, Long documentId, String action, String reason) {
        if (auditService != null && currentUserEmail != null) {
            auditService.log(currentUserEmail, "ACCESS_DENIED", "DOCUMENT", documentId,
                    "Access denied for user " + currentUserEmail + " attempting " + action + ": " + reason);
        }
    }

    public DocumentDTO mapToDTO(Document doc, Long folderId) {
        Optional<DocumentVersion> latestVersionOpt = documentVersionRepository
                .findFirstByDocumentIdOrderByVersionNumberDesc(doc.getId());

        Long fileSize = latestVersionOpt.map(DocumentVersion::getFileSize).orElse(0L);
        String contentType = latestVersionOpt.map(DocumentVersion::getContentType).orElse("application/octet-stream");
        String checksum = latestVersionOpt.map(DocumentVersion::getChecksum).orElse("");

        DocumentDTO dto = new DocumentDTO(
                doc.getId(),
                doc.getTitle(),
                doc.getOriginalFileName(),
                doc.getCategory(),
                doc.getDescription(),
                doc.getOwner() != null ? doc.getOwner().getId() : null,
                folderId != null ? folderId : 1L,
                doc.getStatus() != null ? doc.getStatus().name() : DocumentStatus.ACTIVE.name(),
                doc.getCurrentVersion(),
                doc.getVerificationId(),
                fileSize,
                contentType,
                checksum,
                doc.getCreatedAt(),
                doc.getUpdatedAt()
        );

        dto.setTags(doc.getTags() != null ? new HashSet<>(doc.getTags()) : new HashSet<>());
        dto.setOwnerEmail(doc.getOwner() != null ? doc.getOwner().getEmail() : null);
        dto.setDeleted(doc.isDeleted());
        dto.setDeletedAt(doc.getDeletedAt());
        dto.setOcrStatus(latestVersionOpt.map(DocumentVersion::getOcrStatus).orElse(OcrStatus.PENDING));
        dto.setOcrErrorMessage(latestVersionOpt.map(DocumentVersion::getOcrErrorMessage).orElse(null));

        if (approvalRepository != null && doc.getStatus() == DocumentStatus.REJECTED) {
            approvalRepository.findFirstByDocumentIdOrderByCreatedAtDesc(doc.getId())
                    .ifPresent(app -> dto.setRejectionComment(app.getComments()));
        }

        return dto;
    }

    @Transactional
    public DocumentDTO saveDocument(MultipartFile file, Long userId, Long folderId, String category, String description) {
        return saveDocument(file, userId, folderId, category, description, null);
    }

    @Transactional
    public DocumentDTO saveDocument(MultipartFile file, Long userId, Long folderId, String category, String description, String currentUserEmail) {
        return createDocument(file, userId, null, category, description, new HashSet<>(), currentUserEmail);
    }

    @Transactional
    public DocumentDTO createDocument(MultipartFile file, String title, String category, String description,
                                      Set<String> tags, String currentUserEmail) {
        return createDocument(file, null, title, category, description, tags, currentUserEmail);
    }

    @Transactional
    public DocumentDTO createDocument(MultipartFile file, Long userId, String title, String category, String description,
                                      Set<String> tags, String currentUserEmail) {
        User user = null;
        if (userId != null) {
            user = userRepository.findById(userId).orElse(null);
        }
        if (user == null && currentUserEmail != null) {
            user = userRepository.findByEmail(currentUserEmail).orElse(null);
        }
        if (user == null) {
            user = userRepository.findAll().stream().findFirst()
                    .orElseThrow(() -> new ResourceNotFoundException("No active user found to associate document with"));
        }

        String originalFilename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "document";
        String resolvedTitle = (title != null && !title.isBlank()) ? title.trim() : originalFilename;

        boolean isUploaderAdmin = user.getRoles() != null && user.getRoles().stream()
                .anyMatch(r -> r.getName() == RoleName.ADMIN);

        Document document = new Document();
        document.setTitle(resolvedTitle);
        document.setOriginalFileName(originalFilename);
        document.setCategory(category != null && !category.isBlank() ? category.trim() : "GENERAL");
        document.setDescription(description != null && !description.isBlank() ? description.trim() : "Uploaded document");
        document.setOwner(user);
        document.setStatus(isUploaderAdmin ? DocumentStatus.APPROVED : DocumentStatus.PENDING);
        document.setCurrentVersion(1);
        document.setDeleted(false);
        if (tags != null && !tags.isEmpty()) {
            document.setTags(new HashSet<>(tags));
        }

        Document savedDocument = documentRepository.save(document);

        // Store file physically on filesystem (with validation, magic bytes check, and UUID filename)
        FileStorageService.StoredFileResult storedFile = fileStorageService.storeFile(file, savedDocument.getId(), 1);

        // Record initial version (v1)
        DocumentVersion version = new DocumentVersion(
                savedDocument,
                1,
                storedFile.getFilePath(),
                storedFile.getFileSize(),
                storedFile.getContentType(),
                storedFile.getChecksum(),
                user,
                "Initial upload (v1)"
        );
        version.setOcrStatus(OcrStatus.PENDING);
        DocumentVersion savedVersion = documentVersionRepository.save(version);

        triggerOcrAfterCommit(savedVersion.getId());

        // Create approval row
        if (approvalRepository != null) {
            if (isUploaderAdmin) {
                com.securevault.entity.Approval approval = new com.securevault.entity.Approval(
                        savedDocument,
                        com.securevault.entity.ApprovalStatus.APPROVED,
                        "Auto-approved for administrator upload"
                );
                approval.setReviewer(user);
                approvalRepository.save(approval);
            } else {
                com.securevault.entity.Approval approval = new com.securevault.entity.Approval(
                        savedDocument,
                        com.securevault.entity.ApprovalStatus.PENDING,
                        "Initial document submission"
                );
                approvalRepository.save(approval);
            }
        }

        if (isUploaderAdmin && verificationService != null) {
            verificationService.generate(savedDocument.getId());
        }

        if (auditService != null) {
            auditService.log(user.getId(), "UPLOAD", "DOCUMENT", savedDocument.getId(),
                    "Uploaded document '" + savedDocument.getTitle() + "'");
        }

        return mapToDTO(savedDocument, 1L);
    }

    @Transactional(readOnly = true)
    public PageResponse<DocumentDTO> getDocumentsPaginated(int page, int size, String sortParam,
                                                          String category, String status, String owner,
                                                          String search, String currentUserEmail, boolean isAdmin) {
        Sort sort = Sort.by(Sort.Direction.DESC, "createdAt");
        if (sortParam != null && !sortParam.isBlank()) {
            String[] parts = sortParam.split(",");
            String prop = parts[0].trim();
            Sort.Direction dir = (parts.length > 1 && "asc".equalsIgnoreCase(parts[1].trim()))
                    ? Sort.Direction.ASC
                    : Sort.Direction.DESC;
            sort = Sort.by(dir, prop);
        }

        Pageable pageable = PageRequest.of(page, size, sort);
        Page<Document> pageResult;

        if (isAdmin) {
            pageResult = documentRepository.findAllByDeletedFalse(pageable);
        } else {
            pageResult = documentRepository.findByOwnerEmailAndDeletedFalse(currentUserEmail, pageable);
        }

        List<DocumentDTO> dtoList = pageResult.getContent().stream()
                .filter(doc -> {
                    if (category != null && !category.isBlank() && !category.equalsIgnoreCase(doc.getCategory())) {
                        return false;
                    }
                    if (status != null && !status.isBlank() && !status.equalsIgnoreCase(doc.getStatus().name())) {
                        return false;
                    }
                    if (owner != null && !owner.isBlank() && doc.getOwner() != null) {
                        if (!doc.getOwner().getEmail().equalsIgnoreCase(owner) && !doc.getOwner().getName().equalsIgnoreCase(owner)) {
                            return false;
                        }
                    }
                    if (search != null && !search.isBlank()) {
                        String s = search.toLowerCase();
                        boolean matchesTitle = doc.getTitle() != null && doc.getTitle().toLowerCase().contains(s);
                        boolean matchesName = doc.getOriginalFileName() != null && doc.getOriginalFileName().toLowerCase().contains(s);
                        boolean matchesDesc = doc.getDescription() != null && doc.getDescription().toLowerCase().contains(s);
                        return matchesTitle || matchesName || matchesDesc;
                    }
                    return true;
                })
                .map(doc -> mapToDTO(doc, 1L))
                .collect(Collectors.toList());

        return new PageResponse<>(
                dtoList,
                pageResult.getNumber(),
                pageResult.getSize(),
                pageResult.getTotalElements(),
                pageResult.getTotalPages(),
                pageResult.isLast()
        );
    }

    @Transactional(readOnly = true)
    public List<DocumentDTO> getAllDocuments() {
        return documentRepository.findAllByDeletedFalse().stream()
                .map(doc -> mapToDTO(doc, 1L))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<DocumentDTO> getAllDocumentsForUser(String currentUserEmail, boolean isAdmin) {
        if (isAdmin || currentUserEmail == null) {
            return getAllDocuments();
        }
        return documentRepository.findByOwnerEmailAndDeletedFalse(currentUserEmail).stream()
                .map(doc -> mapToDTO(doc, 1L))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Optional<DocumentDTO> getDocumentById(Long id) {
        return documentRepository.findByIdAndDeletedFalse(id)
                .map(doc -> mapToDTO(doc, 1L));
    }

    @Transactional(readOnly = true)
    public DocumentDTO getDocumentByIdWithAuth(Long id, String currentUserEmail, boolean isAdmin) {
        return getDocumentByIdWithAuth(id, currentUserEmail, isAdmin, false);
    }

    @Transactional(readOnly = true)
    public DocumentDTO getDocumentByIdWithAuth(Long id, String currentUserEmail, boolean isAdmin, boolean isReviewer) {
        Document document = documentRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found with id: " + id));

        if (documentAccessService != null && !documentAccessService.canView(document, currentUserEmail, isAdmin, isReviewer)) {
            logAccessDenied(currentUserEmail, document.getId(), "VIEW_DETAILS", "User does not have view permission");
            throw new ForbiddenException("You do not have permission to view this document");
        }

        if (auditService != null && currentUserEmail != null) {
            auditService.log(currentUserEmail, "VIEW", "DOCUMENT", document.getId(),
                    "Viewed document '" + document.getTitle() + "'");
        }

        return mapToDTO(document, 1L);
    }

    @Transactional(readOnly = true)
    public Resource getDocumentResource(Long documentId) {
        return getDocumentResourceWithAuth(documentId, null, true, false);
    }

    @Transactional(readOnly = true)
    public Resource getDocumentResourceWithAuth(Long documentId, String currentUserEmail, boolean isAdmin) {
        return getDocumentResourceWithAuth(documentId, currentUserEmail, isAdmin, false);
    }

    @Transactional(readOnly = true)
    public Resource getDocumentResourceWithAuth(Long documentId, String currentUserEmail, boolean isAdmin, boolean isReviewer) {
        Document document = documentRepository.findByIdAndDeletedFalse(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found with id: " + documentId));

        if (documentAccessService != null && !documentAccessService.canDownload(document, currentUserEmail, isAdmin, isReviewer)) {
            logAccessDenied(currentUserEmail, document.getId(), "DOWNLOAD", "User does not have download permission");
            throw new ForbiddenException("You do not have permission to download this document");
        }

        DocumentVersion latestVersion = documentVersionRepository
                .findFirstByDocumentIdOrderByVersionNumberDesc(document.getId())
                .orElseThrow(() -> new ResourceNotFoundException("No file version found for document: " + documentId));

        if (auditService != null && currentUserEmail != null) {
            auditService.log(currentUserEmail, "DOWNLOAD", "DOCUMENT", document.getId(),
                    "Downloaded document '" + document.getTitle() + "' version " + latestVersion.getVersionNumber());
        }

        return fileStorageService.loadFileAsResource(latestVersion.getFilePath());
    }

    @Transactional(readOnly = true)
    public Resource getDocumentResourceForViewWithAuth(Long documentId, String currentUserEmail, boolean isAdmin) {
        return getDocumentResourceForViewWithAuth(documentId, currentUserEmail, isAdmin, false);
    }

    @Transactional(readOnly = true)
    public Resource getDocumentResourceForViewWithAuth(Long documentId, String currentUserEmail, boolean isAdmin, boolean isReviewer) {
        Document document = documentRepository.findByIdAndDeletedFalse(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found with id: " + documentId));

        if (documentAccessService != null && !documentAccessService.canView(document, currentUserEmail, isAdmin, isReviewer)) {
            logAccessDenied(currentUserEmail, document.getId(), "VIEW_INLINE", "User does not have view permission");
            throw new ForbiddenException("You do not have permission to view this document");
        }

        DocumentVersion latestVersion = documentVersionRepository
                .findFirstByDocumentIdOrderByVersionNumberDesc(document.getId())
                .orElseThrow(() -> new ResourceNotFoundException("No file version found for document: " + documentId));

        if (auditService != null && currentUserEmail != null) {
            auditService.log(currentUserEmail, "VIEW", "DOCUMENT", document.getId(),
                    "Viewed document '" + document.getTitle() + "' version " + latestVersion.getVersionNumber());
        }

        return fileStorageService.loadFileAsResource(latestVersion.getFilePath());
    }

    @Transactional
    public DocumentDTO updateDocumentDetails(Long id, UpdateDocumentRequest request, String currentUserEmail, boolean isAdmin) {
        Document document = documentRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found with id: " + id));

        if (documentAccessService != null && !documentAccessService.canEdit(document, currentUserEmail, isAdmin)) {
            logAccessDenied(currentUserEmail, document.getId(), "EDIT", "User is not owner or admin");
            throw new ForbiddenException("You do not have permission to edit this document");
        }

        if (request.getTitle() != null && !request.getTitle().isBlank()) {
            document.setTitle(request.getTitle().trim());
        }
        if (request.getCategory() != null) {
            document.setCategory(request.getCategory().trim());
        }
        if (request.getDescription() != null) {
            document.setDescription(request.getDescription().trim());
        }
        if (request.getTags() != null) {
            document.setTags(new HashSet<>(request.getTags()));
        }

        Document updated = documentRepository.save(document);

        if (auditService != null && currentUserEmail != null) {
            auditService.log(currentUserEmail, "UPDATE", "DOCUMENT", updated.getId(),
                    "Updated metadata for document '" + updated.getTitle() + "'");
        }

        return mapToDTO(updated, 1L);
    }

    @Transactional
    public boolean deleteDocument(Long id) {
        return deleteDocumentWithAuth(id, null, true);
    }

    @Transactional
    public boolean deleteDocumentWithAuth(Long id, String currentUserEmail, boolean isAdmin) {
        Document document = documentRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found with id: " + id));

        if (documentAccessService != null && !documentAccessService.canDelete(document, currentUserEmail, isAdmin)) {
            logAccessDenied(currentUserEmail, document.getId(), "DELETE", "User is not owner or admin");
            throw new ForbiddenException("You can only delete your own documents");
        }

        // Soft delete: files remain intact on disk
        document.setDeleted(true);
        document.setDeletedAt(LocalDateTime.now());
        documentRepository.save(document);

        if (auditService != null && currentUserEmail != null) {
            auditService.log(currentUserEmail, "DELETE", "DOCUMENT", document.getId(),
                    "Moved document to trash: '" + document.getTitle() + "'");
        }

        return true;
    }

    @Transactional(readOnly = true)
    public List<DocumentDTO> getTrashDocuments(String currentUserEmail, boolean isAdmin) {
        List<Document> trashed;
        if (isAdmin || currentUserEmail == null) {
            trashed = documentRepository.findAllByDeletedTrue();
        } else {
            trashed = documentRepository.findByOwnerEmailAndDeletedTrue(currentUserEmail);
        }

        return trashed.stream()
                .map(doc -> mapToDTO(doc, 1L))
                .collect(Collectors.toList());
    }

    @Transactional
    public DocumentDTO restoreDocument(Long id, String currentUserEmail, boolean isAdmin) {
        Document document = documentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found with id: " + id));

        if (!document.isDeleted()) {
            return mapToDTO(document, 1L);
        }

        if (documentAccessService != null && !documentAccessService.canRestore(document, currentUserEmail, isAdmin)) {
            logAccessDenied(currentUserEmail, document.getId(), "RESTORE", "User is not owner or admin");
            throw new ForbiddenException("You do not have permission to restore this document");
        }

        document.setDeleted(false);
        document.setDeletedAt(null);
        Document restored = documentRepository.save(document);

        if (auditService != null && currentUserEmail != null) {
            auditService.log(currentUserEmail, "RESTORE", "DOCUMENT", restored.getId(),
                    "Restored document from trash: '" + restored.getTitle() + "'");
        }

        return mapToDTO(restored, 1L);
    }

    @Transactional(readOnly = true)
    public DocumentVersion getLatestVersion(Long documentId) {
        return documentVersionRepository
                .findFirstByDocumentIdOrderByVersionNumberDesc(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("No file version found for document: " + documentId));
    }

    @Transactional(readOnly = true)
    public byte[] getDocumentBytes(Long documentId) {
        DocumentVersion latestVersion = getLatestVersion(documentId);
        return fileStorageService.loadFileAsBytes(latestVersion.getFilePath());
    }

    public DocumentVersionDTO mapVersionToDTO(DocumentVersion version, Integer currentVersionNumber) {
        if (version == null) return null;
        boolean isCurrent = currentVersionNumber != null &&
                            currentVersionNumber.equals(version.getVersionNumber());

        Long uploadedById = version.getUploadedBy() != null ? version.getUploadedBy().getId() : null;
        String uploadedByName = version.getUploadedBy() != null ? version.getUploadedBy().getName() : "Unknown";
        String uploadedByEmail = version.getUploadedBy() != null ? version.getUploadedBy().getEmail() : null;

        DocumentVersionDTO dto = new DocumentVersionDTO(
                version.getId(),
                version.getDocument() != null ? version.getDocument().getId() : null,
                version.getVersionNumber(),
                version.getFilePath(),
                version.getFileSize(),
                version.getContentType(),
                version.getChecksum(),
                uploadedById,
                uploadedByName,
                uploadedByEmail,
                version.getChangeDescription(),
                isCurrent,
                version.getCreatedAt()
        );
        dto.setOcrStatus(version.getOcrStatus());
        dto.setOcrText(version.getOcrText());
        dto.setOcrErrorMessage(version.getOcrErrorMessage());
        return dto;
    }

    /**
     * Phase 5: Create a new version N+1 for an existing document.
     * Validates file with Phase 4 validator.
     * Rejects if checksum matches current version's checksum.
     * Updates document.current_version.
     * Resets status to PENDING if document was APPROVED.
     * Handles concurrency conflicts with 409 Conflict.
     */
    @Transactional
    public DocumentVersionDTO createNewVersion(Long documentId, MultipartFile file, String changeDescription,
                                              String currentUserEmail, boolean isAdmin) {
        Document document = documentRepository.findByIdAndDeletedFalse(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found with id: " + documentId));

        if (documentAccessService != null && !documentAccessService.canEdit(document, currentUserEmail, isAdmin)) {
            logAccessDenied(currentUserEmail, document.getId(), "ADD_VERSION", "User is not owner or admin");
            throw new ForbiddenException("You do not have permission to add new versions to this document");
        }

        // 1. Validate file with Phase 4 validator
        FileValidationUtil.ValidatedFileResult validationResult = fileValidationUtil.validateFile(file);

        // 2. Reject if checksum equals current version's checksum ("no changes detected")
        Optional<DocumentVersion> currentVersionOpt = documentVersionRepository
                .findFirstByDocumentIdOrderByVersionNumberDesc(documentId);

        if (currentVersionOpt.isPresent() &&
                currentVersionOpt.get().getChecksum().equalsIgnoreCase(validationResult.getChecksum())) {
            throw new BadRequestException("No changes detected: File checksum matches the current version (" + validationResult.getChecksum() + ")");
        }

        int nextVersionNumber = currentVersionOpt.map(v -> v.getVersionNumber() + 1)
                .orElse(document.getCurrentVersion() != null ? document.getCurrentVersion() + 1 : 1);

        // 3. User association
        User uploader = null;
        if (currentUserEmail != null) {
            uploader = userRepository.findByEmail(currentUserEmail).orElse(null);
        }
        if (uploader == null) {
            uploader = document.getOwner();
        }

        try {
            // 4. Physical storage under documentId/nextVersionNumber
            FileStorageService.StoredFileResult stored = fileStorageService.storeFile(file, documentId, nextVersionNumber);

            boolean isUploaderAdmin = uploader.getRoles() != null && uploader.getRoles().stream()
                    .anyMatch(r -> r.getName() == RoleName.ADMIN);

            // 5. Version status update
            if (isUploaderAdmin) {
                document.setStatus(DocumentStatus.APPROVED);
                if (verificationService != null) {
                    verificationService.generate(document.getId());
                }
                if (approvalRepository != null) {
                    com.securevault.entity.Approval approval = new com.securevault.entity.Approval(
                            document,
                            com.securevault.entity.ApprovalStatus.APPROVED,
                            "Auto-approved new version v" + nextVersionNumber + " for administrator upload"
                    );
                    approval.setReviewer(uploader);
                    approvalRepository.save(approval);
                }
            } else {
                boolean wasApproved = (document.getStatus() == DocumentStatus.APPROVED);
                if (wasApproved) {
                    document.setStatus(DocumentStatus.PENDING);
                    document.setVerificationId(null);
                }

                if (approvalRepository != null) {
                    com.securevault.entity.Approval approval = new com.securevault.entity.Approval(
                            document,
                            com.securevault.entity.ApprovalStatus.PENDING,
                            "New version v" + nextVersionNumber + " submitted"
                    );
                    approvalRepository.save(approval);
                }
            }

            document.setCurrentVersion(nextVersionNumber);
            documentRepository.save(document);

            DocumentVersion version = new DocumentVersion(
                    document,
                    nextVersionNumber,
                    stored.getFilePath(),
                    stored.getFileSize(),
                    stored.getContentType(),
                    stored.getChecksum(),
                    uploader,
                    changeDescription != null && !changeDescription.isBlank() ? changeDescription.trim() : "Version " + nextVersionNumber
            );
            version.setOcrStatus(OcrStatus.PENDING);

            DocumentVersion saved = documentVersionRepository.save(version);
            triggerOcrAfterCommit(saved.getId());

            if (auditService != null && currentUserEmail != null) {
                auditService.log(currentUserEmail, "VERSION_ADD", "DOCUMENT_VERSION", saved.getId(),
                        "Added version v" + nextVersionNumber + " to document '" + document.getTitle() + "'");
            }

            return mapVersionToDTO(saved, document.getCurrentVersion());
        } catch (DataIntegrityViolationException ex) {
            throw new ConflictException("Concurrent version conflict detected: Version " + nextVersionNumber + " already exists. Please retry.");
        }
    }

    /**
     * Phase 5: List all versions for a document (newest first).
     * Accessible by anyone who can view the document.
     */
    @Transactional(readOnly = true)
    public List<DocumentVersionDTO> getVersionsForDocument(Long documentId, String currentUserEmail, boolean isAdmin) {
        return getVersionsForDocument(documentId, currentUserEmail, isAdmin, false);
    }

    @Transactional(readOnly = true)
    public List<DocumentVersionDTO> getVersionsForDocument(Long documentId, String currentUserEmail, boolean isAdmin, boolean isReviewer) {
        Document document = documentRepository.findByIdAndDeletedFalse(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found with id: " + documentId));

        if (documentAccessService != null && !documentAccessService.canView(document, currentUserEmail, isAdmin, isReviewer)) {
            logAccessDenied(currentUserEmail, document.getId(), "VIEW_VERSIONS", "User does not have view permission");
            throw new ForbiddenException("You do not have permission to view this document's versions");
        }

        List<DocumentVersion> versions = documentVersionRepository.findByDocumentIdOrderByVersionNumberDesc(documentId);
        return versions.stream()
                .map(v -> mapVersionToDTO(v, document.getCurrentVersion()))
                .collect(Collectors.toList());
    }

    /**
     * Phase 5: Get metadata for a specific version.
     */
    @Transactional(readOnly = true)
    public DocumentVersionDTO getVersionMetadata(Long documentId, Long versionId, String currentUserEmail, boolean isAdmin) {
        return getVersionMetadata(documentId, versionId, currentUserEmail, isAdmin, false);
    }

    @Transactional(readOnly = true)
    public DocumentVersionDTO getVersionMetadata(Long documentId, Long versionId, String currentUserEmail, boolean isAdmin, boolean isReviewer) {
        Document document = documentRepository.findByIdAndDeletedFalse(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found with id: " + documentId));

        if (documentAccessService != null && !documentAccessService.canView(document, currentUserEmail, isAdmin, isReviewer)) {
            logAccessDenied(currentUserEmail, document.getId(), "VIEW_VERSION_METADATA", "User does not have view permission");
            throw new ForbiddenException("You do not have permission to view this document");
        }

        DocumentVersion version = findVersionByIdOrNumber(documentId, versionId);
        return mapVersionToDTO(version, document.getCurrentVersion());
    }

    /**
     * Phase 5: Get Resource for downloading or viewing a specific version.
     */
    @Transactional(readOnly = true)
    public Resource getVersionResource(Long documentId, Long versionId, String currentUserEmail, boolean isAdmin) {
        return getVersionResource(documentId, versionId, currentUserEmail, isAdmin, false);
    }

    @Transactional(readOnly = true)
    public Resource getVersionResource(Long documentId, Long versionId, String currentUserEmail, boolean isAdmin, boolean isReviewer) {
        Document document = documentRepository.findByIdAndDeletedFalse(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found with id: " + documentId));

        if (documentAccessService != null && !documentAccessService.canDownload(document, currentUserEmail, isAdmin, isReviewer)) {
            logAccessDenied(currentUserEmail, document.getId(), "DOWNLOAD_VERSION", "User does not have download permission");
            throw new ForbiddenException("You do not have permission to download this document version");
        }

        DocumentVersion version = findVersionByIdOrNumber(documentId, versionId);

        if (auditService != null && currentUserEmail != null) {
            auditService.log(currentUserEmail, "DOWNLOAD", "DOCUMENT", document.getId(),
                    "Downloaded version v" + version.getVersionNumber() + " of document '" + document.getTitle() + "'");
        }

        return fileStorageService.loadFileAsResource(version.getFilePath());
    }

    @Transactional(readOnly = true)
    public Resource getVersionResourceForView(Long documentId, Long versionId, String currentUserEmail, boolean isAdmin) {
        return getVersionResourceForView(documentId, versionId, currentUserEmail, isAdmin, false);
    }

    @Transactional(readOnly = true)
    public Resource getVersionResourceForView(Long documentId, Long versionId, String currentUserEmail, boolean isAdmin, boolean isReviewer) {
        Document document = documentRepository.findByIdAndDeletedFalse(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found with id: " + documentId));

        if (documentAccessService != null && !documentAccessService.canView(document, currentUserEmail, isAdmin, isReviewer)) {
            logAccessDenied(currentUserEmail, document.getId(), "VIEW_VERSION_INLINE", "User does not have view permission");
            throw new ForbiddenException("You do not have permission to view this document version");
        }

        DocumentVersion version = findVersionByIdOrNumber(documentId, versionId);

        if (auditService != null && currentUserEmail != null) {
            auditService.log(currentUserEmail, "VIEW", "DOCUMENT", document.getId(),
                    "Viewed version v" + version.getVersionNumber() + " of document '" + document.getTitle() + "'");
        }

        return fileStorageService.loadFileAsResource(version.getFilePath());
    }

    @Transactional(readOnly = true)
    public DocumentVersion getVersionEntity(Long documentId, Long versionId, String currentUserEmail, boolean isAdmin) {
        return getVersionEntity(documentId, versionId, currentUserEmail, isAdmin, false);
    }

    @Transactional(readOnly = true)
    public DocumentVersion getVersionEntity(Long documentId, Long versionId, String currentUserEmail, boolean isAdmin, boolean isReviewer) {
        Document document = documentRepository.findByIdAndDeletedFalse(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found with id: " + documentId));

        if (documentAccessService != null && !documentAccessService.canView(document, currentUserEmail, isAdmin, isReviewer)) {
            logAccessDenied(currentUserEmail, document.getId(), "VIEW_VERSION_ENTITY", "User does not have view permission");
            throw new ForbiddenException("You do not have permission to view this document");
        }

        DocumentVersion version = findVersionByIdOrNumber(documentId, versionId);

        if (auditService != null && currentUserEmail != null) {
            auditService.log(currentUserEmail, "VIEW", "DOCUMENT", document.getId(),
                    "Viewed version v" + version.getVersionNumber() + " of document '" + document.getTitle() + "'");
        }

        return version;
    }

    private DocumentVersion findVersionByIdOrNumber(Long documentId, Long versionId) {
        return documentVersionRepository.findById(versionId)
                .filter(v -> v.getDocument().getId().equals(documentId))
                .orElseGet(() -> documentVersionRepository
                        .findByDocumentIdAndVersionNumber(documentId, versionId.intValue())
                        .orElseThrow(() -> new ResourceNotFoundException("Version not found with identifier: " + versionId + " for document: " + documentId)));
    }

    /**
     * Phase 5: Restore an older version.
     * Creates a NEW version copying that version's file (new version_number, description "Restored from vX").
     * Never deletes history.
     */
    @Transactional
    public DocumentVersionDTO restoreVersion(Long documentId, Long versionId, String currentUserEmail, boolean isAdmin) {
        Document document = documentRepository.findByIdAndDeletedFalse(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found with id: " + documentId));

        if (documentAccessService != null && !documentAccessService.canEdit(document, currentUserEmail, isAdmin)) {
            logAccessDenied(currentUserEmail, document.getId(), "RESTORE_VERSION", "User is not owner or admin");
            throw new ForbiddenException("You do not have permission to restore versions for this document");
        }

        DocumentVersion sourceVersion = findVersionByIdOrNumber(documentId, versionId);

        int nextVersionNumber = documentVersionRepository.findFirstByDocumentIdOrderByVersionNumberDesc(documentId)
                .map(v -> v.getVersionNumber() + 1)
                .orElse(document.getCurrentVersion() != null ? document.getCurrentVersion() + 1 : 1);

        User uploader = null;
        if (currentUserEmail != null) {
            uploader = userRepository.findByEmail(currentUserEmail).orElse(null);
        }
        if (uploader == null) {
            uploader = document.getOwner();
        }

        try {
            // Copy file physically to the new version folder
            FileStorageService.StoredFileResult copied = fileStorageService.copyVersionFile(
                    sourceVersion.getFilePath(),
                    documentId,
                    nextVersionNumber,
                    sourceVersion.getContentType()
            );

            // If document was approved, reset to PENDING and trigger re-approval
            boolean wasApproved = (document.getStatus() == DocumentStatus.APPROVED);
            if (wasApproved) {
                document.setStatus(DocumentStatus.PENDING);
                document.setVerificationId(null);
            }

            document.setCurrentVersion(nextVersionNumber);
            documentRepository.save(document);

            // Phase 8: On new version of an approved document, create approval row with status PENDING
            if (wasApproved && approvalRepository != null) {
                com.securevault.entity.Approval approval = new com.securevault.entity.Approval(
                        document,
                        com.securevault.entity.ApprovalStatus.PENDING,
                        "Restored to version v" + nextVersionNumber + " submitted"
                );
                approvalRepository.save(approval);
            }

            DocumentVersion restoredVersion = new DocumentVersion(
                    document,
                    nextVersionNumber,
                    copied.getFilePath(),
                    copied.getFileSize(),
                    copied.getContentType(),
                    copied.getChecksum(),
                    uploader,
                    "Restored from v" + sourceVersion.getVersionNumber()
            );

            if (sourceVersion.getOcrText() != null && !sourceVersion.getOcrText().isBlank()) {
                restoredVersion.setOcrText(sourceVersion.getOcrText());
                restoredVersion.setOcrStatus(sourceVersion.getOcrStatus() != null ? sourceVersion.getOcrStatus() : OcrStatus.DONE);
            } else {
                restoredVersion.setOcrStatus(OcrStatus.PENDING);
            }

            DocumentVersion saved = documentVersionRepository.save(restoredVersion);
            if (saved.getOcrStatus() == OcrStatus.PENDING) {
                triggerOcrAfterCommit(saved.getId());
            }

            if (auditService != null && currentUserEmail != null) {
                auditService.log(currentUserEmail, "VERSION_RESTORE", "DOCUMENT_VERSION", saved.getId(),
                        "Restored version v" + sourceVersion.getVersionNumber() + " as v" + nextVersionNumber + " for document '" + document.getTitle() + "'");
            }

            return mapVersionToDTO(saved, document.getCurrentVersion());
        } catch (DataIntegrityViolationException ex) {
            throw new ConflictException("Concurrent version conflict detected while restoring: Version " + nextVersionNumber + " already exists. Please retry.");
        }
    }

    /**
     * Phase 6: Retry OCR on current version of a document.
     * Accessible by document owner or ADMIN.
     */
    @Transactional
    public DocumentDTO retryOcr(Long documentId, String currentUserEmail, boolean isAdmin) {
        Document document = documentRepository.findByIdAndDeletedFalse(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found with id: " + documentId));

        boolean isOwner = document.getOwner() != null &&
                document.getOwner().getEmail() != null &&
                document.getOwner().getEmail().equalsIgnoreCase(currentUserEmail);

        if (!isOwner && !isAdmin) {
            throw new ForbiddenException("Only the document owner or an administrator can retry OCR");
        }

        DocumentVersion currentVersion = documentVersionRepository
                .findByDocumentIdAndVersionNumber(documentId, document.getCurrentVersion())
                .orElseGet(() -> documentVersionRepository
                        .findFirstByDocumentIdOrderByVersionNumberDesc(documentId)
                        .orElseThrow(() -> new ResourceNotFoundException("No version found for document: " + documentId)));

        currentVersion.setOcrStatus(OcrStatus.PENDING);
        currentVersion.setOcrErrorMessage(null);
        DocumentVersion savedVersion = documentVersionRepository.save(currentVersion);

        triggerOcrAfterCommit(savedVersion.getId());

        return mapToDTO(document, 1L);
    }

    /**
     * Triggers async OCR processing strictly AFTER the enclosing transaction commits successfully.
     * Prevents race condition where background worker queries for the version before it is committed.
     */
    private void triggerOcrAfterCommit(Long versionId) {
        if (ocrProcessingService == null || versionId == null) {
            return;
        }
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    ocrProcessingService.processOcrAsync(versionId);
                }
            });
        } else {
            ocrProcessingService.processOcrAsync(versionId);
        }
    }

    /**
     * Phase 6: Advanced Content and Metadata Search with Snippets and Source Attribution.
     */
    @Transactional(readOnly = true)
    public PageResponse<SearchResultDTO> searchDocuments(
            String q,
            String category,
            String statusStr,
            String owner,
            String tag,
            String fromStr,
            String toStr,
            int page,
            int size,
            String currentUserEmail,
            boolean isAdmin) {

        DocumentStatus statusEnum = null;
        if (statusStr != null && !statusStr.isBlank()) {
            try {
                statusEnum = DocumentStatus.valueOf(statusStr.trim().toUpperCase());
            } catch (IllegalArgumentException ignored) {}
        }

        LocalDateTime fromDate = parseDateTime(fromStr, false);
        LocalDateTime toDate = parseDateTime(toStr, true);

        String trimmedQ = (q != null && !q.trim().isEmpty()) ? q.trim() : null;

        // Collect matching document IDs from FULLTEXT if applicable (MySQL only)
        Set<Long> fulltextDocIds = new HashSet<>();
        if (isMySql() && trimmedQ != null && trimmedQ.length() >= 3) {
            try {
                List<Object> nativeResults = documentRepository.searchFulltextNative(trimmedQ);
                if (nativeResults != null) {
                    for (Object res : nativeResults) {
                        if (res instanceof Number) {
                            fulltextDocIds.add(((Number) res).longValue());
                        }
                    }
                }
            } catch (Exception e) {
                // Non-MySQL or no fulltext index fallback
            }
        }

        // Query documents matching filters and/or LIKE
        List<Document> candidates = documentRepository.searchDocumentsLike(
                trimmedQ,
                (category != null && !category.isBlank()) ? category.trim() : null,
                statusEnum,
                (owner != null && !owner.isBlank()) ? owner.trim() : null,
                (tag != null && !tag.isBlank()) ? tag.trim() : null,
                fromDate,
                toDate
        );

        // Security filtering: Only documents the user is allowed to see (ADMIN sees all; user sees own)
        // AND never return soft-deleted documents
        List<Document> authorizedDocs = candidates.stream()
                .filter(doc -> !doc.isDeleted())
                .filter(doc -> documentAccessService == null || documentAccessService.canView(doc, currentUserEmail, isAdmin))
                .sorted((d1, d2) -> {
                    // Fulltext match priority
                    boolean d1Ft = fulltextDocIds.contains(d1.getId());
                    boolean d2Ft = fulltextDocIds.contains(d2.getId());
                    if (d1Ft && !d2Ft) return -1;
                    if (!d1Ft && d2Ft) return 1;
                    // Otherwise sort newest first
                    if (d1.getCreatedAt() != null && d2.getCreatedAt() != null) {
                        return d2.getCreatedAt().compareTo(d1.getCreatedAt());
                    }
                    return Long.compare(d2.getId(), d1.getId());
                })
                .collect(Collectors.toList());

        // Pagination
        int totalElements = authorizedDocs.size();
        int safePage = Math.max(0, page);
        int safeSize = Math.max(1, size);
        int fromIndex = safePage * safeSize;
        int toIndex = Math.min(fromIndex + safeSize, totalElements);

        List<Document> pagedDocs = fromIndex < totalElements
                ? authorizedDocs.subList(fromIndex, toIndex)
                : Collections.emptyList();

        List<SearchResultDTO> results = pagedDocs.stream().map(doc -> {
            DocumentVersion currentVer = documentVersionRepository
                    .findByDocumentIdAndVersionNumber(doc.getId(), doc.getCurrentVersion())
                    .orElseGet(() -> documentVersionRepository
                            .findFirstByDocumentIdOrderByVersionNumberDesc(doc.getId())
                            .orElse(null));

            SnippetUtil.MatchInfo matchInfo = SnippetUtil.extractSnippetAndSource(doc, currentVer, trimmedQ);

            Long fileSize = currentVer != null ? currentVer.getFileSize() : 0L;
            String contentType = currentVer != null ? currentVer.getContentType() : "application/octet-stream";
            OcrStatus ocrStatus = currentVer != null ? currentVer.getOcrStatus() : OcrStatus.PENDING;

            return new SearchResultDTO(
                    doc.getId(),
                    doc.getTitle(),
                    doc.getOriginalFileName(),
                    doc.getCategory(),
                    doc.getStatus() != null ? doc.getStatus().name() : "ACTIVE",
                    doc.getCurrentVersion(),
                    fileSize,
                    contentType,
                    matchInfo.getSnippet(),
                    matchInfo.getMatchSource(),
                    ocrStatus,
                    doc.getOwner() != null ? doc.getOwner().getEmail() : null,
                    doc.getOwner() != null ? doc.getOwner().getName() : null,
                    doc.getTags() != null ? new HashSet<>(doc.getTags()) : new HashSet<>(),
                    doc.getCreatedAt(),
                    doc.getUpdatedAt()
            );
        }).collect(Collectors.toList());

        int totalPages = (int) Math.ceil((double) totalElements / safeSize);
        boolean isLast = safePage >= totalPages - 1;

        return new PageResponse<>(
                results,
                safePage,
                safeSize,
                totalElements,
                totalPages,
                isLast
        );
    }

    private LocalDateTime parseDateTime(String dateStr, boolean endOfDay) {
        if (dateStr == null || dateStr.isBlank()) {
            return null;
        }
        try {
            String trimmed = dateStr.trim();
            if (trimmed.length() == 10 && trimmed.contains("-")) {
                java.time.LocalDate ld = java.time.LocalDate.parse(trimmed);
                return endOfDay ? ld.atTime(23, 59, 59, 999999000) : ld.atStartOfDay();
            }
            return LocalDateTime.parse(trimmed);
        } catch (Exception e) {
            return null;
        }
    }
}
