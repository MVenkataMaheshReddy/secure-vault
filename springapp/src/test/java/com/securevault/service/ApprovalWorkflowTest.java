package com.securevault.service;

import com.securevault.dto.ApprovalDTO;
import com.securevault.dto.ApprovalDecisionRequest;
import com.securevault.dto.DocumentDTO;
import com.securevault.dto.PageResponse;
import com.securevault.entity.Approval;
import com.securevault.entity.ApprovalStatus;
import com.securevault.entity.Document;
import com.securevault.entity.DocumentStatus;
import com.securevault.entity.Role;
import com.securevault.entity.RoleName;
import com.securevault.entity.User;
import com.securevault.exception.BadRequestException;
import com.securevault.exception.ConflictException;
import com.securevault.exception.ForbiddenException;
import com.securevault.repository.ApprovalRepository;
import com.securevault.repository.DocumentRepository;
import com.securevault.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ApprovalWorkflowTest {

    @Mock
    private ApprovalRepository approvalRepository;

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private DocumentService documentService;

    @Mock
    private VerificationService verificationService;

    @InjectMocks
    private ApprovalService approvalService;

    private DocumentAccessService documentAccessService;

    private User employee;
    private User reviewer;
    private User admin;
    private Document document;
    private Approval pendingApproval;

    @BeforeEach
    void setUp() {
        documentAccessService = new DocumentAccessService();

        Role employeeRole = new Role(RoleName.EMPLOYEE);
        Role reviewerRole = new Role(RoleName.REVIEWER);
        Role adminRole = new Role(RoleName.ADMIN);

        employee = new User("alice", "alice@securevault.com", "hash");
        employee.setId(1L);
        employee.setRoles(new HashSet<>(Collections.singleton(employeeRole)));

        reviewer = new User("bob_reviewer", "bob@securevault.com", "hash");
        reviewer.setId(2L);
        reviewer.setRoles(new HashSet<>(Collections.singleton(reviewerRole)));

        admin = new User("carol_admin", "admin@securevault.com", "hash");
        admin.setId(3L);
        admin.setRoles(new HashSet<>(Collections.singleton(adminRole)));

        document = new Document();
        document.setId(10L);
        document.setTitle("Q3 Financial Report");
        document.setOriginalFileName("q3_report.pdf");
        document.setCategory("FINANCIAL");
        document.setOwner(employee);
        document.setStatus(DocumentStatus.PENDING);
        document.setCurrentVersion(1);
        document.setDeleted(false);

        pendingApproval = new Approval(document, ApprovalStatus.PENDING, "Initial document submission");
        pendingApproval.setId(100L);
    }

    @Test
    @DisplayName("Reviewer approves document: updates status to APPROVED, records reviewer, and triggers VerificationService.generate")
    void testApprovePendingDocumentTriggersVerification() {
        when(approvalRepository.findById(100L)).thenReturn(Optional.of(pendingApproval));
        when(userRepository.findByEmail("bob@securevault.com")).thenReturn(Optional.of(reviewer));
        when(approvalRepository.save(any(Approval.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ApprovalDTO result = approvalService.approveApproval(100L, "Looks thorough and compliant.", "bob@securevault.com", true);

        assertNotNull(result);
        assertEquals(ApprovalStatus.APPROVED, result.getStatus());
        assertEquals("Looks thorough and compliant.", result.getComments());
        assertEquals(reviewer.getName(), result.getReviewerName());
        assertEquals(DocumentStatus.APPROVED, document.getStatus());

        // Verify document status updated and saved
        verify(documentRepository, times(1)).save(document);
        // Verify VerificationService.generate was triggered with documentId
        verify(verificationService, times(1)).generate(document.getId());
    }

    @Test
    @DisplayName("Reviewer rejects document with mandatory comments: updates status to REJECTED")
    void testRejectPendingDocumentWithComments() {
        when(approvalRepository.findById(100L)).thenReturn(Optional.of(pendingApproval));
        when(userRepository.findByEmail("bob@securevault.com")).thenReturn(Optional.of(reviewer));
        when(approvalRepository.save(any(Approval.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ApprovalDTO result = approvalService.rejectApproval(100L, "Missing sign-off on section 3.", "bob@securevault.com", true);

        assertNotNull(result);
        assertEquals(ApprovalStatus.REJECTED, result.getStatus());
        assertEquals("Missing sign-off on section 3.", result.getComments());
        assertEquals(reviewer.getName(), result.getReviewerName());
        assertEquals(DocumentStatus.REJECTED, document.getStatus());

        verify(documentRepository, times(1)).save(document);
        verify(verificationService, never()).generate(anyLong());
    }

    @Test
    @DisplayName("Rejection without comments must be rejected with BadRequestException")
    void testRejectWithoutCommentsThrowsBadRequestException() {
        assertThrows(BadRequestException.class, () ->
                approvalService.rejectApproval(100L, null, "bob@securevault.com", true));

        assertThrows(BadRequestException.class, () ->
                approvalService.rejectApproval(100L, "   ", "bob@securevault.com", true));
    }

    @Test
    @DisplayName("Self-approval is blocked: owner cannot approve their own document")
    void testSelfApprovalBlocked() {
        when(approvalRepository.findById(100L)).thenReturn(Optional.of(pendingApproval));

        ForbiddenException ex = assertThrows(ForbiddenException.class, () ->
                approvalService.approveApproval(100L, "Self approved", "alice@securevault.com", true));

        assertTrue(ex.getMessage().contains("cannot approve your own document"));
        verify(verificationService, never()).generate(anyLong());
    }

    @Test
    @DisplayName("Self-rejection is blocked: owner cannot review their own document")
    void testSelfRejectionBlocked() {
        when(approvalRepository.findById(100L)).thenReturn(Optional.of(pendingApproval));

        ForbiddenException ex = assertThrows(ForbiddenException.class, () ->
                approvalService.rejectApproval(100L, "Rejecting myself", "alice@securevault.com", true));

        assertTrue(ex.getMessage().contains("cannot review your own document"));
    }

    @Test
    @DisplayName("Non-pending approvals cannot be decided: throws ConflictException (409)")
    void testCannotDecideAlreadyDecidedApproval() {
        pendingApproval.setStatus(ApprovalStatus.APPROVED);
        when(approvalRepository.findById(100L)).thenReturn(Optional.of(pendingApproval));

        ConflictException exApprove = assertThrows(ConflictException.class, () ->
                approvalService.approveApproval(100L, "Approve again", "bob@securevault.com", true));
        assertTrue(exApprove.getMessage().contains("Only PENDING approval requests can be decided"));

        ConflictException exReject = assertThrows(ConflictException.class, () ->
                approvalService.rejectApproval(100L, "Reject now", "bob@securevault.com", true));
        assertTrue(exReject.getMessage().contains("Only PENDING approval requests can be decided"));
    }

    @Test
    @DisplayName("Role check: non-reviewer and non-admin cannot approve or reject")
    void testRoleCheckForApprovalAndRejection() {
        assertThrows(ForbiddenException.class, () ->
                approvalService.approveApproval(100L, "OK", "user@securevault.com", false));

        assertThrows(ForbiddenException.class, () ->
                approvalService.rejectApproval(100L, "Denied", "user@securevault.com", false));
    }

    @Test
    @DisplayName("Resubmit: owner can resubmit REJECTED document, returning it to PENDING and creating a new approval row")
    void testResubmitRejectedDocument() {
        document.setStatus(DocumentStatus.REJECTED);
        when(documentRepository.findByIdAndDeletedFalse(10L)).thenReturn(Optional.of(document));
        when(documentRepository.save(any(Document.class))).thenAnswer(invocation -> invocation.getArgument(0));

        DocumentDTO mockDto = new DocumentDTO();
        mockDto.setId(10L);
        mockDto.setStatus(DocumentStatus.PENDING.name());
        when(documentService.mapToDTO(any(Document.class), eq(1L))).thenReturn(mockDto);

        DocumentDTO resubmitted = approvalService.resubmitDocument(10L, "alice@securevault.com", false);

        assertEquals(DocumentStatus.PENDING.name(), resubmitted.getStatus());
        assertEquals(DocumentStatus.PENDING, document.getStatus());

        ArgumentCaptor<Approval> approvalCaptor = ArgumentCaptor.forClass(Approval.class);
        verify(approvalRepository, times(1)).save(approvalCaptor.capture());
        Approval capturedApproval = approvalCaptor.getValue();
        assertEquals(ApprovalStatus.PENDING, capturedApproval.getStatus());
        assertEquals(document, capturedApproval.getDocument());
    }

    @Test
    @DisplayName("Resubmit blocked for non-rejected documents: throws ConflictException")
    void testResubmitNonRejectedDocumentBlocked() {
        document.setStatus(DocumentStatus.PENDING);
        when(documentRepository.findByIdAndDeletedFalse(10L)).thenReturn(Optional.of(document));

        ConflictException ex = assertThrows(ConflictException.class, () ->
                approvalService.resubmitDocument(10L, "alice@securevault.com", false));
        assertTrue(ex.getMessage().contains("Only REJECTED documents can be resubmitted"));
    }

    @Test
    @DisplayName("Resubmit blocked for non-owner: throws ForbiddenException")
    void testResubmitByNonOwnerBlocked() {
        document.setStatus(DocumentStatus.REJECTED);
        when(documentRepository.findByIdAndDeletedFalse(10L)).thenReturn(Optional.of(document));

        ForbiddenException ex = assertThrows(ForbiddenException.class, () ->
                approvalService.resubmitDocument(10L, "intruder@securevault.com", false));
        assertTrue(ex.getMessage().contains("Only the document owner can resubmit"));
    }

    @Test
    @DisplayName("DocumentAccessService: Reviewers can view and download PENDING documents even if not the owner")
    void testReviewerAccessToPendingDocuments() {
        document.setStatus(DocumentStatus.PENDING);

        // Reviewer can view and download pending document
        assertTrue(documentAccessService.canView(document, "bob@securevault.com", false, true));
        assertTrue(documentAccessService.canDownload(document, "bob@securevault.com", false, true));

        // Reviewer cannot edit or delete someone else's document
        assertFalse(documentAccessService.canEdit(document, "bob@securevault.com", false));
        assertFalse(documentAccessService.canDelete(document, "bob@securevault.com", false));

        // Other employees cannot view pending document they don't own
        assertFalse(documentAccessService.canView(document, "charlie@securevault.com", false, false));
        assertFalse(documentAccessService.canDownload(document, "charlie@securevault.com", false, false));

        // Admin can view and download everything
        assertTrue(documentAccessService.canView(document, "admin@securevault.com", true, false));
        assertTrue(documentAccessService.canDownload(document, "admin@securevault.com", true, false));
    }

    @Test
    @DisplayName("Approval history is accessible by document owner, reviewer, and admin")
    void testApprovalHistoryAccess() {
        when(documentRepository.findByIdAndDeletedFalse(10L)).thenReturn(Optional.of(document));
        when(approvalRepository.findByDocumentIdOrderByCreatedAtDesc(10L)).thenReturn(List.of(pendingApproval));

        // Owner can access
        List<ApprovalDTO> ownerView = approvalService.getApprovalHistory(10L, "alice@securevault.com", false);
        assertEquals(1, ownerView.size());

        // Reviewer/Admin can access
        List<ApprovalDTO> reviewerView = approvalService.getApprovalHistory(10L, "bob@securevault.com", true);
        assertEquals(1, reviewerView.size());

        // Stranger without reviewer/admin access is blocked
        assertThrows(ForbiddenException.class, () ->
                approvalService.getApprovalHistory(10L, "stranger@securevault.com", false));
    }

    @Test
    @DisplayName("Paginated approvals queue returns pending approvals with document metadata")
    void testGetApprovalsQueue() {
        when(approvalRepository.findByStatus(eq(ApprovalStatus.PENDING), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(pendingApproval)));

        PageResponse<ApprovalDTO> queue = approvalService.getApprovals(ApprovalStatus.PENDING, 0, 10);

        assertNotNull(queue);
        assertEquals(1, queue.getContent().size());
        ApprovalDTO dto = queue.getContent().get(0);
        assertEquals("Q3 Financial Report", dto.getDocumentTitle());
        assertEquals("FINANCIAL", dto.getCategory());
        assertEquals("alice", dto.getOwnerName());
        assertEquals("alice@securevault.com", dto.getOwnerEmail());
        assertEquals(1, dto.getVersion());
        assertEquals(ApprovalStatus.PENDING, dto.getStatus());
    }
}
