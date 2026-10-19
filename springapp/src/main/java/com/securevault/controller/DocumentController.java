package com.securevault.controller;

import com.securevault.dto.DocumentDTO;
import com.securevault.dto.DocumentVersionDTO;
import com.securevault.dto.UpdateDocumentRequest;
import com.securevault.entity.DocumentVersion;
import com.securevault.service.DocumentService;
import java.util.HashSet;
import java.util.List;
import javax.validation.Valid;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import java.nio.charset.StandardCharsets;
import com.securevault.exception.BadRequestException;
import org.springframework.http.ContentDisposition;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "4. Documents", description = "Endpoints for document lifecycle: upload, retrieve, update, delete, version history, search, and QR code access")
@RestController
@RequestMapping({"/api/documents", "/documents"})
public class DocumentController {

    private final DocumentService documentService;
    private final com.securevault.service.VerificationService verificationService;
    private final com.securevault.service.ApprovalService approvalService;

    public DocumentController(DocumentService documentService,
                              @org.springframework.beans.factory.annotation.Autowired(required = false) com.securevault.service.VerificationService verificationService,
                              @org.springframework.beans.factory.annotation.Autowired(required = false) com.securevault.service.ApprovalService approvalService) {
        this.documentService = documentService;
        this.verificationService = verificationService;
        this.approvalService = approvalService;
    }

    private boolean isUserAdmin(Authentication authentication) {
        if (authentication == null) return false;
        return authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    }

    private boolean isUserReviewer(Authentication authentication) {
        if (authentication == null) return false;
        return authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_REVIEWER"));
    }

    private boolean isUserReviewerOrAdmin(Authentication authentication) {
        if (authentication == null) return false;
        return authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN") || a.getAuthority().equals("ROLE_REVIEWER"));
    }

    private boolean isUserVerifierOrAdmin(Authentication authentication) {
        if (authentication == null) return false;
        return authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN") || a.getAuthority().equals("ROLE_VERIFIER"));
    }

    /**
     * Upload document (POST /api/documents and /documents/upload)
     * Creates document and version 1 with strict validation
     */
    @Operation(summary = "Upload new document", description = "Uploads document file, validates magic bytes/MIME, calculates SHA-256 checksum, and initializes version 1")
    @PostMapping(value = {"", "/upload"}, consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN', 'EMPLOYEE')")
    public ResponseEntity<DocumentDTO> uploadDocument(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "title", required = false) String title,
            @RequestParam(value = "category", required = false) String category,
            @RequestParam(value = "description", required = false) String description,
            @RequestParam(value = "tags", required = false) List<String> tags,
            @RequestParam(value = "userId", required = false) Long userId,
            @RequestParam(value = "folderId", required = false, defaultValue = "1") Long folderId,
            Authentication authentication) {

        String email = authentication != null ? authentication.getName() : null;

        DocumentDTO savedDoc;
        if (title != null || (tags != null && !tags.isEmpty())) {
            savedDoc = documentService.createDocument(
                    file,
                    title,
                    category,
                    description,
                    tags != null ? new HashSet<>(tags) : new HashSet<>(),
                    email
            );
        } else {
            savedDoc = documentService.saveDocument(file, userId, folderId, category, description, email);
        }

        return ResponseEntity.ok(savedDoc);
    }

    /**
     * Paginated and filtered documents list (GET /api/documents)
     */
    @Operation(summary = "List documents", description = "Retrieves paginated and filtered list of documents for current user or all for ADMIN")
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> getAllDocuments(
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false, defaultValue = "10") Integer size,
            @RequestParam(value = "sort", required = false, defaultValue = "createdAt,desc") String sort,
            @RequestParam(value = "category", required = false) String category,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "owner", required = false) String owner,
            @RequestParam(value = "search", required = false) String search,
            Authentication authentication) {

        String email = authentication != null ? authentication.getName() : null;
        boolean isAdmin = isUserAdmin(authentication);

        if (search != null && search.length() > 255) {
            throw new BadRequestException("Search query cannot exceed 255 characters");
        }
        int safePage = page != null ? Math.max(0, page) : 0;
        int safeSize = size != null ? Math.min(Math.max(1, size), 100) : 10;

        if (page != null) {
            return ResponseEntity.ok(
                    documentService.getDocumentsPaginated(safePage, safeSize, sort, category, status, owner, search, email, isAdmin)
            );
        }

        if (category != null || status != null || owner != null || search != null) {
            return ResponseEntity.ok(
                    documentService.getDocumentsPaginated(0, safeSize, sort, category, status, owner, search, email, isAdmin)
            );
        }

        List<DocumentDTO> documents = documentService.getAllDocumentsForUser(email, isAdmin);
        return ResponseEntity.ok(documents);
    }

    /**
     * Get soft-deleted documents from trash (GET /api/documents/trash)
     */
    @Operation(summary = "List trash documents", description = "Retrieves soft-deleted documents for the current user or all for ADMIN")
    @GetMapping("/trash")
    @PreAuthorize("hasAnyRole('ADMIN', 'EMPLOYEE')")
    public ResponseEntity<List<DocumentDTO>> getTrashDocuments(Authentication authentication) {
        String email = authentication != null ? authentication.getName() : null;
        boolean isAdmin = isUserAdmin(authentication);
        List<DocumentDTO> trash = documentService.getTrashDocuments(email, isAdmin);
        return ResponseEntity.ok(trash);
    }

    /**
     * Get document metadata and version info by ID (GET /api/documents/{id})
     */
    @Operation(summary = "Get document by ID", description = "Retrieves document metadata and latest version details if authorized")
    @GetMapping({"/{id:[0-9]+}", "/getById/{id:[0-9]+}"})
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<DocumentDTO> getDocumentById(@PathVariable Long id, Authentication authentication) {
        String email = authentication != null ? authentication.getName() : null;
        boolean isAdmin = isUserAdmin(authentication);
        boolean isReviewer = isUserReviewer(authentication);
        DocumentDTO doc = documentService.getDocumentByIdWithAuth(id, email, isAdmin, isReviewer);
        return ResponseEntity.ok(doc);
    }

    /**
     * Download document file (Content-Disposition: attachment)
     */
    @Operation(summary = "Download document", description = "Streams the latest version of the document as an attachment")
    @GetMapping({"/{id:[0-9]+}/download", "/download/{id:[0-9]+}"})
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Resource> downloadDocument(@PathVariable Long id, Authentication authentication) {
        String email = authentication != null ? authentication.getName() : null;
        boolean isAdmin = isUserAdmin(authentication);
        boolean isReviewer = isUserReviewer(authentication);

        DocumentDTO doc = documentService.getDocumentByIdWithAuth(id, email, isAdmin, isReviewer);
        Resource resource = documentService.getDocumentResourceWithAuth(id, email, isAdmin, isReviewer);
        DocumentVersion version = documentService.getLatestVersion(id);

        MediaType mediaType;
        try {
            mediaType = MediaType.parseMediaType(version.getContentType());
        } catch (Exception e) {
            mediaType = MediaType.APPLICATION_OCTET_STREAM;
        }

        String safeFileName = doc.getOriginalFileName() != null ? doc.getOriginalFileName() : ("document_" + id);
        String disposition = ContentDisposition.attachment()
                .filename(safeFileName, StandardCharsets.UTF_8)
                .build()
                .toString();

        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition)
                .body(resource);
    }

    /**
     * View document inline for PDF / images (Content-Disposition: inline)
     */
    @Operation(summary = "View document inline", description = "Streams the latest version of the document with inline disposition for preview")
    @GetMapping({"/{id:[0-9]+}/view", "/view/{id:[0-9]+}"})
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Resource> viewDocument(@PathVariable Long id, Authentication authentication) {
        String email = authentication != null ? authentication.getName() : null;
        boolean isAdmin = isUserAdmin(authentication);
        boolean isReviewer = isUserReviewer(authentication);

        DocumentDTO doc = documentService.getDocumentByIdWithAuth(id, email, isAdmin, isReviewer);
        Resource resource = documentService.getDocumentResourceForViewWithAuth(id, email, isAdmin, isReviewer);
        DocumentVersion version = documentService.getLatestVersion(id);

        MediaType mediaType;
        try {
            mediaType = MediaType.parseMediaType(version.getContentType());
        } catch (Exception e) {
            mediaType = MediaType.APPLICATION_OCTET_STREAM;
        }

        String safeFileName = doc.getOriginalFileName() != null ? doc.getOriginalFileName() : ("document_" + id);
        String disposition = ContentDisposition.inline()
                .filename(safeFileName, StandardCharsets.UTF_8)
                .build()
                .toString();

        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition)
                .body(resource);
    }

    /**
     * Update document details (PUT /api/documents/{id})
     */
    @Operation(summary = "Update document metadata", description = "Updates title, category, description, and tags without altering file content")
    @PutMapping("/{id:[0-9]+}")
    @PreAuthorize("hasAnyRole('ADMIN', 'EMPLOYEE')")
    public ResponseEntity<DocumentDTO> updateDocument(
            @PathVariable Long id,
            @Valid @RequestBody UpdateDocumentRequest request,
            Authentication authentication) {
        String email = authentication != null ? authentication.getName() : null;
        boolean isAdmin = isUserAdmin(authentication);

        DocumentDTO updated = documentService.updateDocumentDetails(id, request, email, isAdmin);
        return ResponseEntity.ok(updated);
    }

    /**
     * Soft delete document (DELETE /api/documents/{id})
     */
    @Operation(summary = "Soft delete document", description = "Flags the document as deleted, moving it to trash without erasing file storage")
    @DeleteMapping({"/{id:[0-9]+}", "/delete/{id:[0-9]+}"})
    @PreAuthorize("hasAnyRole('ADMIN', 'EMPLOYEE')")
    public ResponseEntity<String> deleteDocument(@PathVariable Long id, Authentication authentication) {
        String email = authentication != null ? authentication.getName() : null;
        boolean isAdmin = isUserAdmin(authentication);

        documentService.deleteDocumentWithAuth(id, email, isAdmin);
        return ResponseEntity.ok("Document deleted successfully.");
    }

    /**
     * Restore soft-deleted document (POST /api/documents/{id}/restore)
     */
    @Operation(summary = "Restore document from trash", description = "Restores a soft-deleted document back to active status")
    @PostMapping({"/{id:[0-9]+}/restore", "/restore/{id:[0-9]+}"})
    @PreAuthorize("hasAnyRole('ADMIN', 'EMPLOYEE')")
    public ResponseEntity<DocumentDTO> restoreDocument(@PathVariable Long id, Authentication authentication) {
        String email = authentication != null ? authentication.getName() : null;
        boolean isAdmin = isUserAdmin(authentication);

        DocumentDTO restored = documentService.restoreDocument(id, email, isAdmin);
        return ResponseEntity.ok(restored);
    }

    /**
     * Phase 5: Upload a new version for a document (POST /api/documents/{id}/versions)
     */
    @Operation(summary = "Upload new version", description = "Uploads a new revision of the document, enforcing checksum divergence from current version")
    @PostMapping(value = "/{id:[0-9]+}/versions", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN', 'EMPLOYEE')")
    public ResponseEntity<DocumentVersionDTO> uploadNewVersion(
            @PathVariable Long id,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "changeDescription", required = false) String changeDescription,
            Authentication authentication) {

        String email = authentication != null ? authentication.getName() : null;
        boolean isAdmin = isUserAdmin(authentication);

        DocumentVersionDTO newVersion = documentService.createNewVersion(id, file, changeDescription, email, isAdmin);
        return ResponseEntity.status(org.springframework.http.HttpStatus.CREATED).body(newVersion);
    }

    /**
     * Phase 5: List all versions for a document (GET /api/documents/{id}/versions)
     * Newest first, with isCurrent indicator
     */
    @Operation(summary = "List document versions", description = "Retrieves all versions of the document ordered newest first")
    @GetMapping("/{id:[0-9]+}/versions")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<DocumentVersionDTO>> getDocumentVersions(
            @PathVariable Long id,
            Authentication authentication) {

        String email = authentication != null ? authentication.getName() : null;
        boolean isAdmin = isUserAdmin(authentication);
        boolean isReviewer = isUserReviewer(authentication);

        List<DocumentVersionDTO> versions = documentService.getVersionsForDocument(id, email, isAdmin, isReviewer);
        return ResponseEntity.ok(versions);
    }

    /**
     * Phase 5: Get metadata for a specific version (GET /api/documents/{id}/versions/{versionId})
     */
    @Operation(summary = "Get version metadata", description = "Retrieves metadata for a specific historical version")
    @GetMapping("/{id:[0-9]+}/versions/{versionId:[0-9]+}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<DocumentVersionDTO> getVersionMetadata(
            @PathVariable Long id,
            @PathVariable Long versionId,
            Authentication authentication) {

        String email = authentication != null ? authentication.getName() : null;
        boolean isAdmin = isUserAdmin(authentication);
        boolean isReviewer = isUserReviewer(authentication);

        DocumentVersionDTO versionDTO = documentService.getVersionMetadata(id, versionId, email, isAdmin, isReviewer);
        return ResponseEntity.ok(versionDTO);
    }

    /**
     * Phase 5: Download a specific version file (GET /api/documents/{id}/versions/{versionId}/download)
     */
    @Operation(summary = "Download historical version", description = "Downloads a specific historical version file as an attachment")
    @GetMapping("/{id:[0-9]+}/versions/{versionId:[0-9]+}/download")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Resource> downloadVersion(
            @PathVariable Long id,
            @PathVariable Long versionId,
            Authentication authentication) {

        String email = authentication != null ? authentication.getName() : null;
        boolean isAdmin = isUserAdmin(authentication);
        boolean isReviewer = isUserReviewer(authentication);

        DocumentDTO doc = documentService.getDocumentByIdWithAuth(id, email, isAdmin, isReviewer);
        DocumentVersion version = documentService.getVersionEntity(id, versionId, email, isAdmin, isReviewer);
        Resource resource = documentService.getVersionResource(id, versionId, email, isAdmin, isReviewer);

        MediaType mediaType;
        try {
            mediaType = MediaType.parseMediaType(version.getContentType());
        } catch (Exception e) {
            mediaType = MediaType.APPLICATION_OCTET_STREAM;
        }

        String baseName = doc.getOriginalFileName() != null ? doc.getOriginalFileName() : ("document_" + id);
        String filename = "v" + version.getVersionNumber() + "_" + baseName;
        String disposition = ContentDisposition.attachment()
                .filename(filename, StandardCharsets.UTF_8)
                .build()
                .toString();

        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition)
                .body(resource);
    }

    /**
     * Phase 5: View a specific version file inline (GET /api/documents/{id}/versions/{versionId}/view)
     */
    @Operation(summary = "View historical version inline", description = "Previews a specific historical version file in the browser")
    @GetMapping("/{id:[0-9]+}/versions/{versionId:[0-9]+}/view")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Resource> viewVersion(
            @PathVariable Long id,
            @PathVariable Long versionId,
            Authentication authentication) {

        String email = authentication != null ? authentication.getName() : null;
        boolean isAdmin = isUserAdmin(authentication);
        boolean isReviewer = isUserReviewer(authentication);

        DocumentDTO doc = documentService.getDocumentByIdWithAuth(id, email, isAdmin, isReviewer);
        DocumentVersion version = documentService.getVersionEntity(id, versionId, email, isAdmin, isReviewer);
        Resource resource = documentService.getVersionResourceForView(id, versionId, email, isAdmin, isReviewer);

        MediaType mediaType;
        try {
            mediaType = MediaType.parseMediaType(version.getContentType());
        } catch (Exception e) {
            mediaType = MediaType.APPLICATION_OCTET_STREAM;
        }

        String baseName = doc.getOriginalFileName() != null ? doc.getOriginalFileName() : ("document_" + id);
        String filename = "v" + version.getVersionNumber() + "_" + baseName;
        String disposition = ContentDisposition.inline()
                .filename(filename, StandardCharsets.UTF_8)
                .build()
                .toString();

        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition)
                .body(resource);
    }

    /**
     * Phase 5: Restore an older version (POST /api/documents/{id}/versions/{versionId}/restore)
     * Creates a NEW version copying that version's file. Never deletes history.
     */
    @Operation(summary = "Restore prior version", description = "Creates a new current version copying the contents of a prior version without deleting history")
    @PostMapping("/{id:[0-9]+}/versions/{versionId:[0-9]+}/restore")
    @PreAuthorize("hasAnyRole('ADMIN', 'EMPLOYEE')")
    public ResponseEntity<DocumentVersionDTO> restoreVersion(
            @PathVariable Long id,
            @PathVariable Long versionId,
            Authentication authentication) {

        String email = authentication != null ? authentication.getName() : null;
        boolean isAdmin = isUserAdmin(authentication);

        DocumentVersionDTO restored = documentService.restoreVersion(id, versionId, email, isAdmin);
        return ResponseEntity.ok(restored);
    }

    /**
     * Phase 6: Content and Metadata Search (GET /api/documents/search)
     */
    @Operation(summary = "Search documents", description = "Executes full-text keyword search across metadata and OCR text with permission filtering")
    @GetMapping("/search")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<com.securevault.dto.PageResponse<com.securevault.dto.SearchResultDTO>> searchDocuments(
            @RequestParam(value = "q", required = false) String q,
            @RequestParam(value = "category", required = false) String category,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "owner", required = false) String owner,
            @RequestParam(value = "tag", required = false) String tag,
            @RequestParam(value = "from", required = false) String from,
            @RequestParam(value = "to", required = false) String to,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "10") int size,
            Authentication authentication) {

        if (q != null && q.length() > 255) {
            throw new BadRequestException("Search query cannot exceed 255 characters");
        }
        int safePage = Math.max(0, page);
        int safeSize = Math.min(Math.max(1, size), 100);

        String email = authentication != null ? authentication.getName() : null;
        boolean isAdmin = isUserAdmin(authentication);

        com.securevault.dto.PageResponse<com.securevault.dto.SearchResultDTO> results =
                documentService.searchDocuments(q, category, status, owner, tag, from, to, safePage, safeSize, email, isAdmin);

        return ResponseEntity.ok(results);
    }

    /**
     * Phase 6: Retry OCR on current version (POST /api/documents/{id}/ocr/retry)
     * Accessible by document owner or ADMIN.
     */
    @PostMapping("/{id:[0-9]+}/ocr/retry")
    @PreAuthorize("hasAnyRole('ADMIN', 'EMPLOYEE')")
    public ResponseEntity<DocumentDTO> retryOcr(
            @PathVariable Long id,
            Authentication authentication) {

        String email = authentication != null ? authentication.getName() : null;
        boolean isAdmin = isUserAdmin(authentication);

        DocumentDTO updated = documentService.retryOcr(id, email, isAdmin);
        return ResponseEntity.ok(updated);
    }

    /**
     * Phase 7: Generate verification ID and QR code (POST /api/documents/{id}/verification)
     * ADMIN-only manual generation (idempotent; will be called internally in Phase 8).
     */
    @PostMapping("/{id:[0-9]+}/verification")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<com.securevault.dto.VerificationRecordDTO> generateVerification(
            @PathVariable Long id,
            Authentication authentication) {
        String email = authentication != null ? authentication.getName() : null;
        boolean isAdmin = isUserAdmin(authentication);
        com.securevault.dto.VerificationRecordDTO result = verificationService.generateVerification(id, email, isAdmin);
        return ResponseEntity.ok(result);
    }

    /**
     * Phase 7: Serve document QR code image (GET /api/documents/{id}/qr)
     * Accessible by document owner, ADMIN, or users with view permission.
     */
    @Operation(summary = "Get document QR code image", description = "Serves the generated PNG QR code image for document verification")
    @GetMapping("/{id:[0-9]+}/qr")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Resource> getDocumentQrCode(
            @PathVariable Long id,
            Authentication authentication) {
        String email = authentication != null ? authentication.getName() : null;
        boolean isAdmin = isUserAdmin(authentication);
        Resource qrImage = verificationService.getQrImageResource(id, email, isAdmin);

        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"qr-doc-" + id + ".png\"")
                .body(qrImage);
    }

    /**
     * Revoke document verification (PUT /api/documents/{id}/verification/revoke)
     * Accessible by ADMIN only.
     */
    @Operation(summary = "Revoke document verification", description = "Revokes the active verification status of a document (ADMIN only)")
    @PutMapping("/{id:[0-9]+}/verification/revoke")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<com.securevault.dto.VerificationRecordDTO> revokeVerification(
            @PathVariable Long id,
            Authentication authentication) {
        String email = authentication != null ? authentication.getName() : null;
        boolean isAdmin = isUserAdmin(authentication);
        com.securevault.dto.VerificationRecordDTO result = verificationService.revokeVerification(id, email, isAdmin);
        return ResponseEntity.ok(result);
    }

    /**
     * Phase 8: Get approval history for a document (GET /api/documents/{id}/approvals)
     * Visible to owner, REVIEWER, or ADMIN.
     */
    @GetMapping("/{id:[0-9]+}/approvals")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<com.securevault.dto.ApprovalDTO>> getDocumentApprovals(
            @PathVariable Long id,
            Authentication authentication) {
        String email = authentication != null ? authentication.getName() : null;
        boolean isReviewerOrAdmin = isUserReviewerOrAdmin(authentication);
        List<com.securevault.dto.ApprovalDTO> history = approvalService.getApprovalHistory(id, email, isReviewerOrAdmin);
        return ResponseEntity.ok(history);
    }

    /**
     * Phase 8: Resubmit a rejected document (POST /api/documents/{id}/resubmit)
     * Owner can resubmit after a REJECTED status (creates new PENDING approval).
     */
    @PostMapping("/{id:[0-9]+}/resubmit")
    @PreAuthorize("hasAnyRole('ADMIN', 'EMPLOYEE')")
    public ResponseEntity<DocumentDTO> resubmitDocument(
            @PathVariable Long id,
            Authentication authentication) {
        String email = authentication != null ? authentication.getName() : null;
        boolean isAdmin = isUserAdmin(authentication);
        DocumentDTO resubmitted = approvalService.resubmitDocument(id, email, isAdmin);
        return ResponseEntity.ok(resubmitted);
    }
}
