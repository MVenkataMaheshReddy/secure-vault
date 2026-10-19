package com.securevault.service;

import com.securevault.dto.DocumentDTO;
import com.securevault.dto.DocumentShareDTO;
import com.securevault.dto.PageResponse;
import com.securevault.dto.SearchResultDTO;
import com.securevault.dto.ShareRequest;
import com.securevault.entity.AuditLog;
import com.securevault.entity.Document;
import com.securevault.entity.DocumentShare;
import com.securevault.entity.DocumentStatus;
import com.securevault.entity.DocumentVersion;
import com.securevault.entity.OcrStatus;
import com.securevault.entity.Role;
import com.securevault.entity.RoleName;
import com.securevault.entity.SharePermission;
import com.securevault.entity.User;
import com.securevault.exception.BadRequestException;
import com.securevault.exception.ForbiddenException;
import com.securevault.repository.AuditLogRepository;
import com.securevault.repository.DocumentRepository;
import com.securevault.repository.DocumentShareRepository;
import com.securevault.repository.DocumentVersionRepository;
import com.securevault.repository.UserRepository;
import com.securevault.service.ocr.OcrProcessingService;
import com.securevault.util.FileValidationUtil;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;

import com.securevault.dto.UpdateDocumentRequest;
import org.springframework.core.io.Resource;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class Phase9AcceptanceTest {

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private DocumentVersionRepository documentVersionRepository;

    @Mock
    private DocumentShareRepository documentShareRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private AuditLogRepository auditLogRepository;

    @Mock
    private OcrProcessingService ocrProcessingService;

    @TempDir
    Path tempStorageDir;

    private FileValidationUtil fileValidationUtil;
    private FileStorageService fileStorageService;
    private DocumentAccessService documentAccessService;
    private AuditService auditService;
    private DocumentService documentService;
    private DocumentShareService documentShareService;

    private User aliceOwner;
    private User rahulRecipient;
    private User adminUser;
    private Document contractDoc;
    private DocumentVersion contractVersion;

    @BeforeEach
    void setUp() {
        fileValidationUtil = new FileValidationUtil();
        fileStorageService = new FileStorageService(tempStorageDir.toString(), fileValidationUtil);
        documentAccessService = new DocumentAccessService(documentShareRepository);
        auditService = new AuditService(auditLogRepository, userRepository);

        documentService = new DocumentService(
                documentRepository,
                documentVersionRepository,
                userRepository,
                fileStorageService,
                documentAccessService,
                fileValidationUtil,
                ocrProcessingService,
                auditService
        );

        documentShareService = new DocumentShareService(
                documentShareRepository,
                documentRepository,
                documentVersionRepository,
                userRepository,
                documentAccessService,
                auditService
        );

        // Roles
        Role employeeRole = new Role(RoleName.EMPLOYEE);
        Role adminRole = new Role(RoleName.ADMIN);

        // Alice (Owner)
        aliceOwner = new User("Alice Owner", "alice@securevault.com", "hash");
        aliceOwner.setId(1L);
        aliceOwner.setEnabled(true);
        Set<Role> aliceRoles = new HashSet<>();
        aliceRoles.add(employeeRole);
        aliceOwner.setRoles(aliceRoles);

        // Rahul (Colleague / Recipient)
        rahulRecipient = new User("Rahul Verma", "rahul@securevault.com", "hash");
        rahulRecipient.setId(2L);
        rahulRecipient.setEnabled(true);
        Set<Role> rahulRoles = new HashSet<>();
        rahulRoles.add(employeeRole);
        rahulRecipient.setRoles(rahulRoles);

        // Admin
        adminUser = new User("System Admin", "admin@securevault.com", "hash");
        adminUser.setId(99L);
        adminUser.setEnabled(true);
        Set<Role> adminRoles = new HashSet<>();
        adminRoles.add(adminRole);
        adminUser.setRoles(adminRoles);

        // Alice's Document
        contractDoc = new Document("Confidential Partnership Agreement", "partnership.pdf", "LEGAL", "Joint venture terms", aliceOwner);
        contractDoc.setId(500L);
        contractDoc.setCurrentVersion(1);
        contractDoc.setStatus(DocumentStatus.APPROVED);
        contractDoc.setDeleted(false);

        // Save a dummy file in temp storage for download/view
        Path filePath = tempStorageDir.resolve("partnership.pdf");
        try {
            java.nio.file.Files.writeString(filePath, "%PDF-1.4 Mock Partnership Agreement Content");
        } catch (Exception e) {
            fail("Failed to write mock file: " + e.getMessage());
        }

        contractVersion = new DocumentVersion(
                contractDoc,
                1,
                filePath.toString(),
                1024L,
                "application/pdf",
                "checksum_hash_123",
                aliceOwner,
                "Initial version"
        );
        contractVersion.setId(5001L);
    }

    // =========================================================================
    // Acceptance Requirement 1: Rahul can view but not download a VIEW-shared doc
    // =========================================================================
    @Test
    @DisplayName("Acceptance: Rahul can view but cannot download a VIEW-shared document")
    void testRahulCanViewButCannotDownloadViewSharedDoc() {
        DocumentShare viewShare = new DocumentShare(contractDoc, aliceOwner, rahulRecipient, SharePermission.VIEW, null);
        viewShare.setId(701L);

        when(documentShareRepository.findByDocumentIdAndSharedWithEmail(500L, "rahul@securevault.com"))
                .thenReturn(Optional.of(viewShare));
        when(documentRepository.findByIdAndDeletedFalse(500L)).thenReturn(Optional.of(contractDoc));
        when(documentVersionRepository.findFirstByDocumentIdOrderByVersionNumberDesc(500L))
                .thenReturn(Optional.of(contractVersion));

        // 1. Permission checks
        assertTrue(documentAccessService.canView(contractDoc, "rahul@securevault.com", false),
                "Rahul must be allowed to VIEW the document");
        assertFalse(documentAccessService.canDownload(contractDoc, "rahul@securevault.com", false),
                "Rahul must NOT be allowed to DOWNLOAD the VIEW-shared document");

        // 2. View operation succeeds
        assertDoesNotThrow(() -> {
            Resource res = documentService.getDocumentResourceForViewWithAuth(500L, "rahul@securevault.com", false);
            assertNotNull(res);
            assertTrue(res.exists());
        });

        // 3. Download operation is blocked with ForbiddenException (403)
        ForbiddenException ex = assertThrows(ForbiddenException.class, () -> {
            documentService.getDocumentResourceWithAuth(500L, "rahul@securevault.com", false);
        });
        assertTrue(ex.getMessage().toLowerCase().contains("download"),
                "Exception message should indicate download permission is required");
    }

    // =========================================================================
    // Acceptance Requirement 2: Rahul with DOWNLOAD share can both view and download
    // =========================================================================
    @Test
    @DisplayName("Acceptance: Rahul with DOWNLOAD permission can both view and download")
    void testRahulWithDownloadPermissionCanBothViewAndDownload() {
        DocumentShare downloadShare = new DocumentShare(contractDoc, aliceOwner, rahulRecipient, SharePermission.DOWNLOAD, null);
        downloadShare.setId(702L);

        when(documentShareRepository.findByDocumentIdAndSharedWithEmail(500L, "rahul@securevault.com"))
                .thenReturn(Optional.of(downloadShare));
        when(documentRepository.findByIdAndDeletedFalse(500L)).thenReturn(Optional.of(contractDoc));
        when(documentVersionRepository.findFirstByDocumentIdOrderByVersionNumberDesc(500L))
                .thenReturn(Optional.of(contractVersion));

        assertTrue(documentAccessService.canView(contractDoc, "rahul@securevault.com", false));
        assertTrue(documentAccessService.canDownload(contractDoc, "rahul@securevault.com", false));

        // Both view and download succeed
        assertDoesNotThrow(() -> {
            Resource viewRes = documentService.getDocumentResourceForViewWithAuth(500L, "rahul@securevault.com", false);
            assertNotNull(viewRes);
            Resource downloadRes = documentService.getDocumentResourceWithAuth(500L, "rahul@securevault.com", false);
            assertNotNull(downloadRes);
        });
    }

    // =========================================================================
    // Acceptance Requirement 3: Revoke removes access immediately
    // =========================================================================
    @Test
    @DisplayName("Acceptance: Revoke removes access immediately")
    void testRevokeRemovesAccessImmediately() {
        DocumentShare activeShare = new DocumentShare(contractDoc, aliceOwner, rahulRecipient, SharePermission.VIEW, null);
        activeShare.setId(703L);

        when(documentRepository.findById(500L)).thenReturn(Optional.of(contractDoc));
        when(documentShareRepository.findById(703L)).thenReturn(Optional.of(activeShare));

        // Alice revokes Rahul's share
        documentShareService.revokeShare(500L, 703L, "alice@securevault.com", false);

        // Verify share was deleted from DB
        verify(documentShareRepository).delete(activeShare);

        // Verify audit log was recorded for SHARE_REVOKE
        verify(auditLogRepository).save(any(AuditLog.class));

        // Subsequent access lookup returns empty
        when(documentShareRepository.findByDocumentIdAndSharedWithEmail(500L, "rahul@securevault.com"))
                .thenReturn(Optional.empty());

        assertFalse(documentAccessService.canView(contractDoc, "rahul@securevault.com", false),
                "Access must be denied immediately after revocation");
        assertFalse(documentAccessService.canDownload(contractDoc, "rahul@securevault.com", false));
    }

    // =========================================================================
    // Acceptance Requirement 4: Expired share is ignored at access time
    // =========================================================================
    @Test
    @DisplayName("Acceptance: Expired shares are ignored at access time")
    void testExpiredShareIgnoredAtAccessTime() {
        LocalDateTime pastExpiry = LocalDateTime.now().minusMinutes(5);
        DocumentShare expiredShare = new DocumentShare(contractDoc, aliceOwner, rahulRecipient, SharePermission.DOWNLOAD, pastExpiry);
        expiredShare.setId(704L);

        when(documentShareRepository.findByDocumentIdAndSharedWithEmail(500L, "rahul@securevault.com"))
                .thenReturn(Optional.of(expiredShare));
        when(documentRepository.findByIdAndDeletedFalse(500L)).thenReturn(Optional.of(contractDoc));

        assertFalse(documentAccessService.canView(contractDoc, "rahul@securevault.com", false),
                "Expired share must deny viewing");
        assertFalse(documentAccessService.canDownload(contractDoc, "rahul@securevault.com", false),
                "Expired share must deny downloading");

        assertThrows(ForbiddenException.class, () ->
                documentService.getDocumentResourceForViewWithAuth(500L, "rahul@securevault.com", false));
        assertThrows(ForbiddenException.class, () ->
                documentService.getDocumentResourceWithAuth(500L, "rahul@securevault.com", false));
    }

    // =========================================================================
    // Acceptance Requirement 5: Shared users cannot edit, delete, share or add versions
    // =========================================================================
    @Test
    @DisplayName("Acceptance: Shared user can NEVER edit, delete, share or add versions")
    void testSharedUserCannotEditDeleteShareOrAddVersions() {
        when(documentRepository.findByIdAndDeletedFalse(500L)).thenReturn(Optional.of(contractDoc));
        when(documentRepository.findById(500L)).thenReturn(Optional.of(contractDoc));

        // 1. Cannot Edit
        assertFalse(documentAccessService.canEdit(contractDoc, "rahul@securevault.com", false));
        assertThrows(ForbiddenException.class, () ->
                documentService.updateDocumentDetails(500L, new UpdateDocumentRequest("New Title", "LEGAL", "New Desc", null), "rahul@securevault.com", false));

        // 2. Cannot Delete
        assertFalse(documentAccessService.canDelete(contractDoc, "rahul@securevault.com", false));
        assertThrows(ForbiddenException.class, () ->
                documentService.deleteDocumentWithAuth(500L, "rahul@securevault.com", false));

        // 3. Cannot Share
        assertFalse(documentAccessService.canShare(contractDoc, "rahul@securevault.com", false));
        ShareRequest shareReq = new ShareRequest("thirdparty@example.com", SharePermission.VIEW, null);
        assertThrows(AccessDeniedException.class, () ->
                documentShareService.shareDocument(500L, shareReq, "rahul@securevault.com", false));

        // 4. Cannot Add Version
        MockMultipartFile newVersionFile = new MockMultipartFile("file", "v2.pdf", "application/pdf", "%PDF-1.4 sample".getBytes());
        assertThrows(ForbiddenException.class, () ->
                documentService.createNewVersion(500L, newVersionFile, "attempt by recipient", "rahul@securevault.com", false));
    }

    // =========================================================================
    // Acceptance Requirement 6: Search results include shared documents
    // =========================================================================
    @Test
    @DisplayName("Acceptance: Search results include active shared documents for the recipient")
    void testSearchIncludesSharedDocumentsForRecipient() {
        DocumentShare viewShare = new DocumentShare(contractDoc, aliceOwner, rahulRecipient, SharePermission.VIEW, null);

        // When Rahul searches, candidates query returns Alice's doc
        when(documentRepository.searchDocumentsLike(eq("Partnership"), any(), any(), any(), any(), any(), any()))
                .thenReturn(List.of(contractDoc));
        when(documentShareRepository.findByDocumentIdAndSharedWithEmail(500L, "rahul@securevault.com"))
                .thenReturn(Optional.of(viewShare));
        when(documentVersionRepository.findByDocumentIdAndVersionNumber(500L, 1))
                .thenReturn(Optional.of(contractVersion));

        PageResponse<SearchResultDTO> searchResults = documentService.searchDocuments(
                "Partnership", null, null, null, null, null, null, 0, 10, "rahul@securevault.com", false
        );

        assertNotNull(searchResults);
        assertEquals(1, searchResults.getContent().size(),
                "Search must include Alice's document because it is shared with Rahul");
        assertEquals(500L, searchResults.getContent().get(0).getId());
    }

    // =========================================================================
    // Acceptance Requirement 7: Audit row written for demo flow & resilience
    // =========================================================================
    @Test
    @DisplayName("Acceptance: Every action in the demo flow is logged to audit repository")
    void testAuditRowWrittenForActions() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(aliceOwner));

        // 1. Audit user login
        auditService.log(1L, "LOGIN_SUCCESS", "USER", 1L, "User login successful");

        // 2. Audit share creation
        auditService.log(1L, "SHARE_CREATE", "DOCUMENT_SHARE", 701L, "Shared with rahul@securevault.com");

        // 3. Audit public verification lookup
        auditService.logPublic("VERIFY", "VERIFICATION_RECORD", 999L, "Public verification check: SV-12345678");

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository, times(3)).save(captor.capture());

        List<AuditLog> savedLogs = captor.getAllValues();
        assertEquals("LOGIN_SUCCESS", savedLogs.get(0).getAction());
        assertEquals("SHARE_CREATE", savedLogs.get(1).getAction());
        assertEquals("VERIFY", savedLogs.get(2).getAction());
        assertNull(savedLogs.get(2).getUser(), "Public verification log must have null user");
    }

    @Test
    @DisplayName("Acceptance: Audit database failure NEVER breaks user business workflow")
    void testAuditFailureNeverBreaksBusinessWorkflow() {
        // Mock audit repository failing
        doThrow(new RuntimeException("Audit DB Down")).when(auditLogRepository).save(any(AuditLog.class));
        when(userRepository.findById(1L)).thenReturn(Optional.of(aliceOwner));

        // Audit call does NOT throw exception
        assertDoesNotThrow(() -> {
            auditService.log(1L, "DOWNLOAD", "DOCUMENT", 500L, "Downloaded document");
        }, "Audit service must catch exceptions internally and never bubble up to break workflow");
    }
}
