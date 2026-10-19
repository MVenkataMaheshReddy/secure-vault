package com.securevault.service;

import com.securevault.dto.FileCheckResponseDTO;
import com.securevault.dto.PublicVerificationDTO;
import com.securevault.dto.VerificationRecordDTO;
import com.securevault.entity.Document;
import com.securevault.entity.DocumentStatus;
import com.securevault.entity.DocumentVersion;
import com.securevault.entity.VerificationRecord;
import com.securevault.entity.VerificationStatus;
import com.securevault.exception.BadRequestException;
import com.securevault.exception.ForbiddenException;
import com.securevault.exception.ResourceNotFoundException;
import com.securevault.repository.ApprovalRepository;
import com.securevault.repository.DocumentRepository;
import com.securevault.repository.DocumentVersionRepository;
import com.securevault.repository.VerificationRecordRepository;
import com.securevault.util.FileValidationUtil;
import com.securevault.util.InMemoryRateLimiter;
import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class VerificationService {

    private static final Logger logger = LoggerFactory.getLogger(VerificationService.class);

    private static final java.util.regex.Pattern VERIFICATION_ID_PATTERN =
            java.util.regex.Pattern.compile("^SV-[0-9A-Fa-f]{8}$");

    private final DocumentRepository documentRepository;
    private final DocumentVersionRepository documentVersionRepository;
    private final VerificationRecordRepository verificationRecordRepository;
    private final QrCodeService qrCodeService;
    private final DocumentAccessService documentAccessService;
    private final InMemoryRateLimiter inMemoryRateLimiter;
    private ApprovalRepository approvalRepository;
    private AuditService auditService;

    public VerificationService(
            DocumentRepository documentRepository,
            DocumentVersionRepository documentVersionRepository,
            VerificationRecordRepository verificationRecordRepository,
            QrCodeService qrCodeService,
            DocumentAccessService documentAccessService,
            InMemoryRateLimiter inMemoryRateLimiter) {
        this(documentRepository, documentVersionRepository, verificationRecordRepository, qrCodeService, documentAccessService, inMemoryRateLimiter, null, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public VerificationService(
            DocumentRepository documentRepository,
            DocumentVersionRepository documentVersionRepository,
            VerificationRecordRepository verificationRecordRepository,
            QrCodeService qrCodeService,
            DocumentAccessService documentAccessService,
            InMemoryRateLimiter inMemoryRateLimiter,
            @org.springframework.beans.factory.annotation.Autowired(required = false) ApprovalRepository approvalRepository,
            @org.springframework.beans.factory.annotation.Autowired(required = false) AuditService auditService) {
        this.documentRepository = documentRepository;
        this.documentVersionRepository = documentVersionRepository;
        this.verificationRecordRepository = verificationRecordRepository;
        this.qrCodeService = qrCodeService;
        this.documentAccessService = documentAccessService;
        this.inMemoryRateLimiter = inMemoryRateLimiter;
        this.approvalRepository = approvalRepository;
        this.auditService = auditService;
    }

    /**
     * Generates a verification record and ZXing QR code for a document.
     * Idempotent: If already generated, returns existing record.
     * Accessible by ADMIN.
     */
    @Transactional
    public VerificationRecordDTO generateVerification(Long documentId, String currentUserEmail, boolean isAdmin) {
        if (!isAdmin) {
            throw new ForbiddenException("Only administrators can generate verification records");
        }

        return generate(documentId);
    }

    /**
     * Core generator: Generates a verification record and ZXing QR code for an APPROVED document.
     * Idempotent: If already generated, returns existing record.
     * Documents that are PENDING or REJECTED get no reference.
     * Called directly on approval in the same transaction or by administrator.
     */
    @Transactional
    public VerificationRecordDTO generate(Long documentId) {
        Document document = documentRepository.findByIdAndDeletedFalse(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found with id: " + documentId));

        // Eligibility: Only APPROVED documents can get a verification reference
        if (document.getStatus() != DocumentStatus.APPROVED) {
            throw new BadRequestException("Documents that are PENDING or REJECTED cannot have verification generated. Document must be APPROVED.");
        }

        // Idempotency check: if verification already exists, return existing
        if (document.getVerificationId() != null) {
            VerificationRecord existing = verificationRecordRepository.findByVerificationId(document.getVerificationId())
                    .orElse(null);
            if (existing != null) {
                logger.info("Verification already exists for document {}: {}", documentId, existing.getVerificationId());
                return mapToDTO(existing);
            }
        }

        java.util.Optional<VerificationRecord> existingRecordOpt = verificationRecordRepository.findByDocumentId(document.getId());
        if (existingRecordOpt.isPresent()) {
            VerificationRecord existing = existingRecordOpt.get();
            if (existing.getStatus() != VerificationStatus.VALID) {
                existing.setStatus(VerificationStatus.VALID);
                verificationRecordRepository.save(existing);
            }
            if (document.getVerificationId() == null || !document.getVerificationId().equals(existing.getVerificationId())) {
                document.setVerificationId(existing.getVerificationId());
                documentRepository.save(document);
            }
            logger.info("Verification record already exists for document {}: {}", documentId, existing.getVerificationId());
            return mapToDTO(existing);
        }

        // Generate unique verification ID in format "SV-" + 8 uppercase hex chars
        String verificationId;
        int attempts = 0;
        do {
            if (attempts++ > 20) {
                throw new RuntimeException("Failed to generate a unique verification ID after 20 attempts");
            }
            verificationId = qrCodeService.generateVerificationId();
        } while (documentRepository.existsByVerificationId(verificationId)
                || verificationRecordRepository.existsByVerificationId(verificationId));

        document.setVerificationId(verificationId);
        documentRepository.save(document);

        VerificationRecord record = new VerificationRecord();
        record.setDocument(document);
        record.setVerificationId(verificationId);
        record.setQrPath("/api/documents/" + document.getId() + "/qr");
        record.setStatus(VerificationStatus.VALID);
        VerificationRecord savedRecord = verificationRecordRepository.save(record);

        logger.info("Generated verification ID {} for document {}", verificationId, documentId);
        return mapToDTO(savedRecord);
    }

    /**
     * Serves the QR code PNG image for a document, generated on the fly.
     * Accessible by document owner, ADMIN, or users with view permission.
     */
    @Transactional(readOnly = true)
    public Resource getQrImageResource(Long documentId, String currentUserEmail, boolean isAdmin) {
        Document document = documentRepository.findByIdAndDeletedFalse(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found with id: " + documentId));

        if (!canViewDocument(document, currentUserEmail, isAdmin)) {
            if (auditService != null && currentUserEmail != null) {
                auditService.log(currentUserEmail, "ACCESS_DENIED", "DOCUMENT_QR", documentId,
                        "Access denied for user " + currentUserEmail + " attempting to view QR code for document " + documentId);
            }
            throw new ForbiddenException("You do not have permission to view this document's QR code");
        }

        if (document.getVerificationId() == null) {
            throw new BadRequestException("Verification QR code has not been generated for this document");
        }

        String verificationUrl = qrCodeService.buildVerificationUrl(document.getVerificationId());
        byte[] qrBytes = qrCodeService.generateQrImageBytes(verificationUrl, QrCodeService.QR_WIDTH, QrCodeService.QR_HEIGHT);
        return new org.springframework.core.io.ByteArrayResource(qrBytes);
    }

    /**
     * Public endpoint: returns safe verification details.
     * Rate-limited to 30 req/min/IP.
     * Never returns file paths, file contents, or email addresses.
     * Validates ID format via regex before querying the database.
     */
    @Transactional(readOnly = true)
    public PublicVerificationDTO getPublicVerification(String verificationId, String clientIp) {
        inMemoryRateLimiter.checkLimit(clientIp);

        if (verificationId == null || !VERIFICATION_ID_PATTERN.matcher(verificationId.trim()).matches()) {
            throw new ResourceNotFoundException("Verification reference not found");
        }

        VerificationRecord record = verificationRecordRepository.findByVerificationId(verificationId.trim())
                .orElseThrow(() -> new ResourceNotFoundException("Verification reference not found"));

        Document document = record.getDocument();

        // Status rules: VALID only if verification record is VALID AND document is APPROVED AND not soft-deleted.
        // Otherwise report REVOKED, never VALID.
        boolean isValid = (record.getStatus() == VerificationStatus.VALID)
                && (document != null)
                && (!document.isDeleted())
                && (document.getStatus() == DocumentStatus.APPROVED);

        String status = isValid ? "VALID" : "REVOKED";

        DocumentVersion currentVersion = null;
        if (document != null) {
            currentVersion = documentVersionRepository
                    .findByDocumentIdAndVersionNumber(document.getId(), document.getCurrentVersion())
                    .orElseGet(() -> documentVersionRepository
                            .findFirstByDocumentIdOrderByVersionNumberDesc(document.getId())
                            .orElse(null));
        }

        String checksum = currentVersion != null ? currentVersion.getChecksum() : "";
        String uploadedBy = (document != null && document.getOwner() != null && document.getOwner().getName() != null)
                ? document.getOwner().getName()
                : "SecureVault Verified User";

        java.time.LocalDateTime approvedAt = record.getCreatedAt();
        if (document != null && approvalRepository != null) {
            java.util.Optional<com.securevault.entity.Approval> approvedOpt =
                    approvalRepository.findFirstByDocumentIdAndStatus(document.getId(), com.securevault.entity.ApprovalStatus.APPROVED);
            if (approvedOpt.isPresent()) {
                java.time.LocalDateTime updatedAt = approvedOpt.get().getUpdatedAt();
                if (updatedAt != null) {
                    approvedAt = updatedAt;
                }
            }
        }

        if (auditService != null) {
            auditService.logPublic("VERIFY", "VERIFICATION_RECORD", record.getId(),
                    "Public verification lookup for verificationId: " + record.getVerificationId());
        }

        return new PublicVerificationDTO(
                record.getVerificationId(),
                document != null ? document.getTitle() : "Document",
                document != null ? document.getCategory() : "GENERAL",
                uploadedBy,
                status,
                approvedAt,
                document != null ? document.getCurrentVersion() : 1,
                checksum
        );
    }

    /**
     * Public endpoint: checks whether an uploaded file copy matches any version of the document.
     * Rate-limited. Does NOT store the uploaded file.
     */
    public FileCheckResponseDTO checkFileAuthenticity(String verificationId, MultipartFile file, String clientIp) {
        inMemoryRateLimiter.checkLimit(clientIp);

        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Uploaded file cannot be empty");
        }

        if (verificationId == null || !VERIFICATION_ID_PATTERN.matcher(verificationId.trim()).matches()) {
            throw new ResourceNotFoundException("Verification reference not found");
        }

        VerificationRecord record = verificationRecordRepository.findByVerificationId(verificationId.trim())
                .orElseThrow(() -> new ResourceNotFoundException("Verification reference not found"));

        Document document = record.getDocument();
        if (document == null || document.isDeleted()) {
            throw new ResourceNotFoundException("Verification reference not found");
        }

        // Compute SHA-256 checksum in memory without writing to disk
        String uploadedChecksum;
        try {
            uploadedChecksum = FileValidationUtil.computeSha256(file.getBytes());
        } catch (IOException e) {
            throw new BadRequestException("Failed to read uploaded file: " + e.getMessage());
        }

        List<DocumentVersion> versions = documentVersionRepository.findByDocumentIdOrderByVersionNumberDesc(document.getId());

        DocumentVersion currentVersion = versions.stream()
                .filter(v -> v.getVersionNumber().equals(document.getCurrentVersion()))
                .findFirst()
                .orElse(versions.isEmpty() ? null : versions.get(0));

        String currentVerChecksum = currentVersion != null ? currentVersion.getChecksum() : "";

        // Check if uploaded checksum matches any version
        DocumentVersion matchedVersion = versions.stream()
                .filter(v -> v.getChecksum() != null && v.getChecksum().equalsIgnoreCase(uploadedChecksum))
                .findFirst()
                .orElse(null);

        boolean matches = (matchedVersion != null);
        Integer matchedVersionNum = matchedVersion != null ? matchedVersion.getVersionNumber() : null;
        String status = record.getStatus().name();

        String message;
        if (matches) {
            if (record.getStatus() == VerificationStatus.REVOKED) {
                message = "Uploaded file matches version " + matchedVersionNum + ", but this document has been REVOKED.";
            } else {
                message = "Document copy is authentic! Checksum matches official version " + matchedVersionNum + ".";
            }
        } else {
            message = "Checksum mismatch. The uploaded file does not match any official version of this document.";
        }

        if (auditService != null) {
            auditService.logPublic("VERIFY", "VERIFICATION_RECORD", record.getId(),
                    "Public file authenticity check for verificationId: " + record.getVerificationId() +
                    (matches ? " [MATCHED v" + matchedVersionNum + "]" : " [NO_MATCH]"));
        }

        return new FileCheckResponseDTO(
                record.getVerificationId(),
                matches,
                matchedVersionNum,
                uploadedChecksum,
                currentVerChecksum,
                status,
                message
        );
    }

    /**
     * Revokes verification for a document.
     * Accessible by ADMIN only.
     */
    @Transactional
    public VerificationRecordDTO revokeVerification(Long documentId, String currentUserEmail, boolean isAdmin) {
        if (!isAdmin) {
            throw new ForbiddenException("Only administrators can revoke document verification");
        }

        Document document = documentRepository.findByIdAndDeletedFalse(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found with id: " + documentId));

        VerificationRecord record = verificationRecordRepository.findByDocumentId(document.getId())
                .orElseThrow(() -> new ResourceNotFoundException("No verification record found for document ID: " + documentId));

        record.setStatus(VerificationStatus.REVOKED);
        VerificationRecord saved = verificationRecordRepository.save(record);
        logger.warn("Verification revoked for document ID {} (verification ID {}) by {}",
                documentId, record.getVerificationId(), currentUserEmail);

        if (auditService != null) {
            auditService.log(currentUserEmail, "REVOKE", "VERIFICATION_RECORD", saved.getId(),
                    "Revoked verification for document '" + document.getTitle() + "' (ID: " + record.getVerificationId() + ")");
        }

        return mapToDTO(saved);
    }

    /**
     * Retrieves all verification records for Verifier Dashboard.
     * Accessible by ADMIN or VERIFIER.
     */
    @Transactional(readOnly = true)
    public List<VerificationRecordDTO> getAllVerifications(String currentUserEmail, boolean isAdminOrVerifier) {
        if (!isAdminOrVerifier) {
            throw new ForbiddenException("Only administrators and verifiers can view the verifications list");
        }

        List<VerificationRecord> records = verificationRecordRepository.findAllByOrderByCreatedAtDesc();
        return records.stream().map(this::mapToDTO).collect(Collectors.toList());
    }

    private boolean canViewDocument(Document document, String currentUserEmail, boolean isAdmin) {
        if (isAdmin) return true;
        if (currentUserEmail != null && document.getOwner() != null
                && currentUserEmail.equalsIgnoreCase(document.getOwner().getEmail())) {
            return true;
        }
        if (documentAccessService != null) {
            return documentAccessService.canView(document, currentUserEmail, isAdmin);
        }
        return false;
    }

    private VerificationRecordDTO mapToDTO(VerificationRecord record) {
        Document doc = record.getDocument();
        return new VerificationRecordDTO(
                record.getId(),
                doc != null ? doc.getId() : null,
                doc != null ? doc.getTitle() : "Unknown Document",
                doc != null ? doc.getCategory() : null,
                record.getVerificationId(),
                record.getStatus(),
                doc != null ? doc.getCurrentVersion() : 1,
                (doc != null && doc.getOwner() != null) ? doc.getOwner().getName() : "Unknown",
                record.getCreatedAt()
        );
    }
}
