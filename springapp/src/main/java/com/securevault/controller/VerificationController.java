package com.securevault.controller;

import com.securevault.dto.FileCheckResponseDTO;
import com.securevault.dto.PublicVerificationDTO;
import com.securevault.dto.VerificationRecordDTO;
import com.securevault.service.VerificationService;
import java.util.List;
import javax.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "9. Verification & QR", description = "Public and administrative endpoints for cryptographic document verification and file authenticity checks")
@RestController
@RequestMapping({"/api/verify", "/verify"})
public class VerificationController {

    private final VerificationService verificationService;

    public VerificationController(VerificationService verificationService) {
        this.verificationService = verificationService;
    }

    /**
     * Public endpoint: verifies a document by verification ID.
     * No authentication required.
     * Returns ONLY safe non-sensitive metadata (no email, no file paths, no file content).
     * Rate-limited to 30 req/min/IP.
     */
    @Operation(summary = "Public verify document", description = "Verifies document by verification ID, returning safe public metadata with zero confidential leaks")
    @GetMapping("/{verificationId}")
    public ResponseEntity<PublicVerificationDTO> verifyDocument(
            @PathVariable String verificationId,
            HttpServletRequest request) {
        String clientIp = getClientIp(request);
        PublicVerificationDTO dto = verificationService.getPublicVerification(verificationId, clientIp);
        return ResponseEntity.ok(dto);
    }

    /**
     * Public endpoint: checks whether an uploaded file matches any official version of the document.
     * No authentication required.
     * Does NOT store the uploaded file.
     * Rate-limited.
     */
    @Operation(summary = "Check file authenticity", description = "Validates if an uploaded physical file matches any approved version via SHA-256 checksum without storing the upload")
    @PostMapping(value = "/{verificationId}/check-file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<FileCheckResponseDTO> checkFileAuthenticity(
            @PathVariable String verificationId,
            @RequestParam("file") MultipartFile file,
            HttpServletRequest request) {
        String clientIp = getClientIp(request);
        FileCheckResponseDTO response = verificationService.checkFileAuthenticity(verificationId, file, clientIp);
        return ResponseEntity.ok(response);
    }

    /**
     * Verifier & Admin Dashboard: list all verification records.
     */
    @Operation(summary = "List verification records", description = "Retrieves all issued verification records and status (VERIFIER or ADMIN only)")
    @GetMapping("/records")
    @PreAuthorize("hasAnyRole('ADMIN', 'VERIFIER')")
    public ResponseEntity<List<VerificationRecordDTO>> getVerificationRecords(Authentication authentication) {
        String email = authentication != null ? authentication.getName() : null;
        boolean isAdminOrVerifier = isUserVerifierOrAdmin(authentication);
        List<VerificationRecordDTO> records = verificationService.getAllVerifications(email, isAdminOrVerifier);
        return ResponseEntity.ok(records);
    }

    private boolean isUserVerifierOrAdmin(Authentication authentication) {
        if (authentication == null) return false;
        return authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN") || a.getAuthority().equals("ROLE_VERIFIER"));
    }

    private String getClientIp(HttpServletRequest request) {
        if (request == null) return "UNKNOWN";
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
