package com.securevault.service;

import com.securevault.dto.ApprovalDTO;
import com.securevault.dto.DocumentDTO;
import com.securevault.dto.PageResponse;
import com.securevault.entity.Approval;
import com.securevault.entity.ApprovalStatus;
import com.securevault.entity.Document;
import com.securevault.entity.DocumentStatus;
import com.securevault.entity.User;
import com.securevault.exception.BadRequestException;
import com.securevault.exception.ConflictException;
import com.securevault.exception.ForbiddenException;
import com.securevault.exception.ResourceNotFoundException;
import com.securevault.repository.ApprovalRepository;
import com.securevault.repository.DocumentRepository;
import com.securevault.repository.UserRepository;
import java.util.List;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ApprovalService {

    private static final Logger logger = LoggerFactory.getLogger(ApprovalService.class);

    private final ApprovalRepository approvalRepository;
    private final DocumentRepository documentRepository;
    private final UserRepository userRepository;
    private final DocumentService documentService;
    private VerificationService verificationService;
    private AuditService auditService;

    public ApprovalService(ApprovalRepository approvalRepository,
                           DocumentRepository documentRepository,
                           UserRepository userRepository,
                           DocumentService documentService,
                           @Autowired(required = false) VerificationService verificationService) {
        this(approvalRepository, documentRepository, userRepository, documentService, verificationService, null);
    }

    @Autowired
    public ApprovalService(ApprovalRepository approvalRepository,
                           DocumentRepository documentRepository,
                           UserRepository userRepository,
                           DocumentService documentService,
                           @Autowired(required = false) VerificationService verificationService,
                           @Autowired(required = false) AuditService auditService) {
        this.approvalRepository = approvalRepository;
        this.documentRepository = documentRepository;
        this.userRepository = userRepository;
        this.documentService = documentService;
        this.verificationService = verificationService;
        this.auditService = auditService;
    }

    @Autowired(required = false)
    public void setVerificationService(VerificationService verificationService) {
        this.verificationService = verificationService;
    }

    @Autowired(required = false)
    public void setAuditService(AuditService auditService) {
        this.auditService = auditService;
    }

    /**
     * Map Approval entity to ApprovalDTO.
     */
    public ApprovalDTO mapToDTO(Approval approval) {
        if (approval == null) return null;
        Document doc = approval.getDocument();
        User reviewer = approval.getReviewer();
        User owner = doc != null ? doc.getOwner() : null;

        String originalFileName = doc != null ? doc.getOriginalFileName() : null;
        String fileType = "UNKNOWN";
        if (originalFileName != null && originalFileName.contains(".")) {
            fileType = originalFileName.substring(originalFileName.lastIndexOf(".") + 1).toUpperCase();
        }

        ApprovalDTO dto = new ApprovalDTO(
                approval.getId(),
                doc != null ? doc.getId() : null,
                doc != null ? doc.getTitle() : "Unknown Document",
                doc != null ? doc.getCategory() : "GENERAL",
                owner != null ? owner.getName() : "Unknown Owner",
                owner != null ? owner.getEmail() : null,
                doc != null ? doc.getCurrentVersion() : 1,
                approval.getStatus(),
                reviewer != null ? reviewer.getId() : null,
                reviewer != null ? reviewer.getName() : null,
                reviewer != null ? reviewer.getEmail() : null,
                approval.getComments(),
                approval.getCreatedAt(),
                approval.getUpdatedAt()
        );
        dto.setOriginalFileName(originalFileName);
        dto.setFileType(fileType);
        return dto;
    }

    /**
     * Get pending approvals count.
     */
    @Transactional(readOnly = true)
    public long getPendingCount() {
        return approvalRepository.countByStatus(ApprovalStatus.PENDING);
    }

    /**
     * Paginated list of approvals, optionally filtered by status (default PENDING).
     */
    @Transactional(readOnly = true)
    public PageResponse<ApprovalDTO> getApprovals(ApprovalStatus status, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Approval> pageResult;
        if (status != null) {
            pageResult = approvalRepository.findByStatus(status, pageable);
        } else {
            pageResult = approvalRepository.findAll(pageable);
        }

        List<ApprovalDTO> dtoList = pageResult.getContent().stream()
                .map(this::mapToDTO)
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

    /**
     * Get single approval by ID.
     */
    @Transactional(readOnly = true)
    public ApprovalDTO getApprovalById(Long id, String currentUserEmail, boolean isReviewerOrAdmin) {
        Approval approval = approvalRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Approval request not found with id: " + id));

        Document doc = approval.getDocument();
        boolean isOwner = doc != null && doc.getOwner() != null
                && doc.getOwner().getEmail() != null
                && doc.getOwner().getEmail().equalsIgnoreCase(currentUserEmail);

        if (!isOwner && !isReviewerOrAdmin) {
            throw new ForbiddenException("You do not have permission to view this approval request");
        }

        return mapToDTO(approval);
    }

    /**
     * Approve document.
     * Only REVIEWER or ADMIN.
     * Cannot approve own document.
     * Only PENDING approvals can be decided (409 otherwise).
     * Updates document status and approval row in one transaction.
     * Calls VerificationService.generate(documentId).
     */
    @Transactional
    public ApprovalDTO approveApproval(Long approvalId, String comments, String reviewerEmail, boolean isReviewerOrAdmin) {
        if (!isReviewerOrAdmin) {
            throw new ForbiddenException("Only reviewers or administrators can approve documents");
        }

        Approval approval = approvalRepository.findById(approvalId)
                .orElseThrow(() -> new ResourceNotFoundException("Approval request not found with id: " + approvalId));

        if (approval.getStatus() != ApprovalStatus.PENDING) {
            throw new ConflictException("Only PENDING approval requests can be decided. Current status: " + approval.getStatus());
        }

        Document document = approval.getDocument();
        if (document == null || document.isDeleted()) {
            throw new ResourceNotFoundException("Associated document not found or has been deleted");
        }

        // Self-approval check: a user cannot approve their own document
        if (document.getOwner() != null && document.getOwner().getEmail() != null
                && document.getOwner().getEmail().equalsIgnoreCase(reviewerEmail)) {
            throw new ForbiddenException("You cannot approve your own document");
        }

        User reviewer = userRepository.findByEmail(reviewerEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Reviewer user record not found: " + reviewerEmail));

        approval.setStatus(ApprovalStatus.APPROVED);
        approval.setReviewer(reviewer);
        if (comments != null && !comments.isBlank()) {
            approval.setComments(comments.trim());
        }
        Approval savedApproval = approvalRepository.save(approval);

        document.setStatus(DocumentStatus.APPROVED);
        documentRepository.save(document);

        // Phase 8: On approve call VerificationService.generate(documentId)
        if (verificationService != null) {
            verificationService.generate(document.getId());
        }

        logger.info("Approval {} APPROVED by {} for document {}", approvalId, reviewerEmail, document.getId());

        if (auditService != null) {
            auditService.log(reviewer.getId(), "APPROVE", "DOCUMENT_APPROVAL", savedApproval.getId(),
                    "Approved document '" + document.getTitle() + "'");
        }

        return mapToDTO(savedApproval);
    }

    /**
     * Reject document.
     * Only REVIEWER or ADMIN.
     * Comments are required.
     * Cannot review own document.
     * Only PENDING approvals can be decided (409 otherwise).
     * Updates document status and approval row in one transaction.
     */
    @Transactional
    public ApprovalDTO rejectApproval(Long approvalId, String comments, String reviewerEmail, boolean isReviewerOrAdmin) {
        if (!isReviewerOrAdmin) {
            throw new ForbiddenException("Only reviewers or administrators can reject documents");
        }

        if (comments == null || comments.trim().isEmpty()) {
            throw new BadRequestException("Comments are required when rejecting an approval request");
        }

        Approval approval = approvalRepository.findById(approvalId)
                .orElseThrow(() -> new ResourceNotFoundException("Approval request not found with id: " + approvalId));

        if (approval.getStatus() != ApprovalStatus.PENDING) {
            throw new ConflictException("Only PENDING approval requests can be decided. Current status: " + approval.getStatus());
        }

        Document document = approval.getDocument();
        if (document == null || document.isDeleted()) {
            throw new ResourceNotFoundException("Associated document not found or has been deleted");
        }

        // Self-decision check
        if (document.getOwner() != null && document.getOwner().getEmail() != null
                && document.getOwner().getEmail().equalsIgnoreCase(reviewerEmail)) {
            throw new ForbiddenException("You cannot review your own document");
        }

        User reviewer = userRepository.findByEmail(reviewerEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Reviewer user record not found: " + reviewerEmail));

        approval.setStatus(ApprovalStatus.REJECTED);
        approval.setReviewer(reviewer);
        approval.setComments(comments.trim());
        Approval savedApproval = approvalRepository.save(approval);

        document.setStatus(DocumentStatus.REJECTED);
        documentRepository.save(document);

        logger.info("Approval {} REJECTED by {} for document {}", approvalId, reviewerEmail, document.getId());

        if (auditService != null) {
            auditService.log(reviewer.getId(), "REJECT", "DOCUMENT_APPROVAL", savedApproval.getId(),
                    "Rejected document '" + document.getTitle() + "' with reason: " + comments.trim());
        }

        return mapToDTO(savedApproval);
    }

    /**
     * Get approval history for a document.
     * Visible to owner, REVIEWER, or ADMIN.
     */
    @Transactional(readOnly = true)
    public List<ApprovalDTO> getApprovalHistory(Long documentId, String currentUserEmail, boolean isReviewerOrAdmin) {
        Document document = documentRepository.findByIdAndDeletedFalse(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found with id: " + documentId));

        boolean isOwner = document.getOwner() != null
                && document.getOwner().getEmail() != null
                && document.getOwner().getEmail().equalsIgnoreCase(currentUserEmail);

        if (!isOwner && !isReviewerOrAdmin) {
            throw new ForbiddenException("You do not have permission to view approval history for this document");
        }

        return approvalRepository.findByDocumentIdOrderByCreatedAtDesc(documentId).stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    /**
     * Resubmit a rejected document.
     * Owner (or ADMIN) can resubmit after a REJECTED status.
     * Creates new PENDING approval row and sets document status to PENDING.
     */
    @Transactional
    public DocumentDTO resubmitDocument(Long documentId, String currentUserEmail, boolean isAdmin) {
        Document document = documentRepository.findByIdAndDeletedFalse(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found with id: " + documentId));

        boolean isOwner = document.getOwner() != null
                && document.getOwner().getEmail() != null
                && document.getOwner().getEmail().equalsIgnoreCase(currentUserEmail);

        if (!isOwner && !isAdmin) {
            throw new ForbiddenException("Only the document owner can resubmit this document for approval");
        }

        if (document.getStatus() != DocumentStatus.REJECTED) {
            throw new ConflictException("Only REJECTED documents can be resubmitted. Current status: " + document.getStatus());
        }

        document.setStatus(DocumentStatus.PENDING);
        Document savedDoc = documentRepository.save(document);

        Approval newApproval = new Approval(savedDoc, ApprovalStatus.PENDING, "Resubmitted for review");
        approvalRepository.save(newApproval);

        logger.info("Document {} resubmitted for approval by {}", documentId, currentUserEmail);
        return documentService.mapToDTO(savedDoc, 1L);
    }
}
