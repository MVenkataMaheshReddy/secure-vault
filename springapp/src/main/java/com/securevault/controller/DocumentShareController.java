package com.securevault.controller;

import com.securevault.dto.DocumentShareDTO;
import com.securevault.dto.ShareRequest;
import com.securevault.dto.SharedDocumentDTO;
import com.securevault.service.DocumentShareService;
import java.util.List;
import javax.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "7. Document Sharing", description = "Endpoints for document sharing, permissions delegation, active shares listing, and share revocation")
@RestController
@RequestMapping("/api/documents")
public class DocumentShareController {

    private final DocumentShareService documentShareService;

    public DocumentShareController(DocumentShareService documentShareService) {
        this.documentShareService = documentShareService;
    }

    private boolean isUserAdmin(Authentication authentication) {
        if (authentication == null) return false;
        return authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    }

    /**
     * Share a document with another user (owner or ADMIN).
     * POST /api/documents/{id}/shares
     */
    @Operation(summary = "Share document", description = "Grants VIEW or DOWNLOAD permission on a document to another user")
    @PostMapping("/{id}/shares")
    @PreAuthorize("hasAnyRole('ADMIN', 'EMPLOYEE')")
    public ResponseEntity<DocumentShareDTO> shareDocument(
            @PathVariable("id") Long documentId,
            @Valid @RequestBody ShareRequest request,
            Authentication authentication) {
        boolean isAdmin = isUserAdmin(authentication);
        String currentUserEmail = authentication.getName();
        DocumentShareDTO result = documentShareService.shareDocument(documentId, request, currentUserEmail, isAdmin);
        return new ResponseEntity<>(result, HttpStatus.CREATED);
    }

    /**
     * Get list of all shares for a document (owner or ADMIN).
     * GET /api/documents/{id}/shares
     */
    @Operation(summary = "List document shares", description = "Retrieves all active shares for a specific document (owner or ADMIN)")
    @GetMapping("/{id}/shares")
    @PreAuthorize("hasAnyRole('ADMIN', 'EMPLOYEE')")
    public ResponseEntity<List<DocumentShareDTO>> getDocumentShares(
            @PathVariable("id") Long documentId,
            Authentication authentication) {
        boolean isAdmin = isUserAdmin(authentication);
        String currentUserEmail = authentication.getName();
        List<DocumentShareDTO> shares = documentShareService.getDocumentShares(documentId, currentUserEmail, isAdmin);
        return ResponseEntity.ok(shares);
    }

    /**
     * Revoke a document share (owner or ADMIN).
     * DELETE /api/documents/{id}/shares/{shareId}
     */
    @Operation(summary = "Revoke document share", description = "Revokes a user's shared access to a document")
    @DeleteMapping("/{id}/shares/{shareId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'EMPLOYEE')")
    public ResponseEntity<Void> revokeShare(
            @PathVariable("id") Long documentId,
            @PathVariable("shareId") Long shareId,
            Authentication authentication) {
        boolean isAdmin = isUserAdmin(authentication);
        String currentUserEmail = authentication.getName();
        documentShareService.revokeShare(documentId, shareId, currentUserEmail, isAdmin);
        return ResponseEntity.noContent().build();
    }

    /**
     * Get all active documents shared with the current authenticated user.
     * GET /api/documents/shared-with-me
     */
    @Operation(summary = "List documents shared with me", description = "Retrieves paginated documents shared with the currently authenticated user")
    @GetMapping("/shared-with-me")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Page<SharedDocumentDTO>> getDocumentsSharedWithMe(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt") String sort,
            @RequestParam(defaultValue = "desc") String direction,
            Authentication authentication) {
        int safePage = Math.max(0, page);
        int safeSize = Math.min(Math.max(1, size), 100);
        Sort sortObj = direction.equalsIgnoreCase("desc")
                ? Sort.by(sort).descending()
                : Sort.by(sort).ascending();
        Pageable pageable = PageRequest.of(safePage, safeSize, sortObj);
        String currentUserEmail = authentication.getName();
        Page<SharedDocumentDTO> sharedDocs = documentShareService.getDocumentsSharedWithMe(currentUserEmail, pageable);
        return ResponseEntity.ok(sharedDocs);
    }
}
