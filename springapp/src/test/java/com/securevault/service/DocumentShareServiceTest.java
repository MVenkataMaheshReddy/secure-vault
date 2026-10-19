package com.securevault.service;

import com.securevault.dto.DocumentShareDTO;
import com.securevault.dto.ShareRequest;
import com.securevault.entity.Document;
import com.securevault.entity.DocumentShare;
import com.securevault.entity.Role;
import com.securevault.entity.RoleName;
import com.securevault.entity.SharePermission;
import com.securevault.entity.User;
import com.securevault.exception.BadRequestException;
import com.securevault.exception.ResourceNotFoundException;
import com.securevault.repository.DocumentRepository;
import com.securevault.repository.DocumentShareRepository;
import com.securevault.repository.DocumentVersionRepository;
import com.securevault.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class DocumentShareServiceTest {

    @Mock
    private DocumentShareRepository documentShareRepository;

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private DocumentVersionRepository documentVersionRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private AuditService auditService;

    private DocumentAccessService documentAccessService;
    private DocumentShareService documentShareService;

    private User owner;
    private User recipientRahul;
    private User thirdParty;
    private Document doc;

    @BeforeEach
    public void setup() {
        documentAccessService = new DocumentAccessService(documentShareRepository);
        documentShareService = new DocumentShareService(
                documentShareRepository,
                documentRepository,
                documentVersionRepository,
                userRepository,
                documentAccessService,
                auditService
        );

        owner = new User();
        owner.setId(1L);
        owner.setName("Alice Owner");
        owner.setEmail("alice@securevault.com");
        owner.setEnabled(true);
        Role employeeRole = new Role(RoleName.EMPLOYEE);
        Set<Role> roles = new HashSet<>();
        roles.add(employeeRole);
        owner.setRoles(roles);

        recipientRahul = new User();
        recipientRahul.setId(2L);
        recipientRahul.setName("Rahul Verma");
        recipientRahul.setEmail("rahul@securevault.com");
        recipientRahul.setEnabled(true);
        recipientRahul.setRoles(roles);

        thirdParty = new User();
        thirdParty.setId(3L);
        thirdParty.setName("Eve Outsider");
        thirdParty.setEmail("eve@external.com");
        thirdParty.setEnabled(true);
        thirdParty.setRoles(roles);

        doc = new Document();
        doc.setId(100L);
        doc.setTitle("Confidential Strategy");
        doc.setOwner(owner);
        doc.setDeleted(false);
        doc.setCurrentVersion(1);
    }

    @Test
    public void testOwnerCanShareDocumentWithViewPermission() {
        when(documentRepository.findById(100L)).thenReturn(Optional.of(doc));
        when(userRepository.findByEmail("alice@securevault.com")).thenReturn(Optional.of(owner));
        when(userRepository.findByEmail("rahul@securevault.com")).thenReturn(Optional.of(recipientRahul));
        when(documentShareRepository.findByDocumentIdAndSharedWithId(100L, 2L)).thenReturn(Optional.empty());

        DocumentShare saved = new DocumentShare(doc, owner, recipientRahul, SharePermission.VIEW, null);
        saved.setId(10L);
        when(documentShareRepository.save(any(DocumentShare.class))).thenReturn(saved);

        ShareRequest req = new ShareRequest("rahul@securevault.com", SharePermission.VIEW, null);
        DocumentShareDTO dto = documentShareService.shareDocument(100L, req, "alice@securevault.com", false);

        assertNotNull(dto);
        assertEquals(SharePermission.VIEW, dto.getPermission());
        assertEquals("rahul@securevault.com", dto.getSharedWithEmail());
        verify(documentShareRepository).save(any(DocumentShare.class));
        verify(auditService).log(eq(owner), eq("SHARE_CREATE"), eq("DOCUMENT_SHARE"), eq(10L), anyString());
    }

    @Test
    public void testCannotShareWithSelf() {
        when(documentRepository.findById(100L)).thenReturn(Optional.of(doc));

        ShareRequest req = new ShareRequest("alice@securevault.com", SharePermission.VIEW, null);
        assertThrows(BadRequestException.class, () ->
                documentShareService.shareDocument(100L, req, "alice@securevault.com", false));
    }

    @Test
    public void testCannotShareWithDocumentOwner() {
        when(documentRepository.findById(100L)).thenReturn(Optional.of(doc));

        ShareRequest req = new ShareRequest("alice@securevault.com", SharePermission.VIEW, null);
        assertThrows(BadRequestException.class, () ->
                documentShareService.shareDocument(100L, req, "admin@securevault.com", true));
    }

    @Test
    public void testNonOwnerCannotShare() {
        when(documentRepository.findById(100L)).thenReturn(Optional.of(doc));

        ShareRequest req = new ShareRequest("rahul@securevault.com", SharePermission.VIEW, null);
        assertThrows(AccessDeniedException.class, () ->
                documentShareService.shareDocument(100L, req, "eve@external.com", false));
    }

    @Test
    public void testDuplicateShareUpdatesExistingRecord() {
        when(documentRepository.findById(100L)).thenReturn(Optional.of(doc));
        when(userRepository.findByEmail("alice@securevault.com")).thenReturn(Optional.of(owner));
        when(userRepository.findByEmail("rahul@securevault.com")).thenReturn(Optional.of(recipientRahul));

        DocumentShare existing = new DocumentShare(doc, owner, recipientRahul, SharePermission.VIEW, null);
        existing.setId(10L);
        when(documentShareRepository.findByDocumentIdAndSharedWithId(100L, 2L)).thenReturn(Optional.of(existing));
        when(documentShareRepository.save(any(DocumentShare.class))).thenAnswer(invocation -> invocation.getArgument(0));

        LocalDateTime expiry = LocalDateTime.now().plusDays(7);
        ShareRequest updateReq = new ShareRequest("rahul@securevault.com", SharePermission.DOWNLOAD, expiry);
        DocumentShareDTO result = documentShareService.shareDocument(100L, updateReq, "alice@securevault.com", false);

        assertEquals(SharePermission.DOWNLOAD, result.getPermission());
        assertEquals(expiry, result.getExpiresAt());
    }

    @Test
    public void testPermissionMatrix_ViewOnlyUserCannotDownload() {
        DocumentShare share = new DocumentShare(doc, owner, recipientRahul, SharePermission.VIEW, null);
        when(documentShareRepository.findByDocumentIdAndSharedWithEmail(100L, "rahul@securevault.com"))
                .thenReturn(Optional.of(share));

        assertTrue(documentAccessService.canView(doc, "rahul@securevault.com", false));
        assertFalse(documentAccessService.canDownload(doc, "rahul@securevault.com", false));
        assertFalse(documentAccessService.canEdit(doc, "rahul@securevault.com", false));
        assertFalse(documentAccessService.canDelete(doc, "rahul@securevault.com", false));
    }

    @Test
    public void testPermissionMatrix_DownloadPermissionAllowsBothViewAndDownload() {
        DocumentShare share = new DocumentShare(doc, owner, recipientRahul, SharePermission.DOWNLOAD, null);
        when(documentShareRepository.findByDocumentIdAndSharedWithEmail(100L, "rahul@securevault.com"))
                .thenReturn(Optional.of(share));

        assertTrue(documentAccessService.canView(doc, "rahul@securevault.com", false));
        assertTrue(documentAccessService.canDownload(doc, "rahul@securevault.com", false));
        assertFalse(documentAccessService.canEdit(doc, "rahul@securevault.com", false));
        assertFalse(documentAccessService.canDelete(doc, "rahul@securevault.com", false));
    }

    @Test
    public void testExpiredShareDeniesBothViewAndDownload() {
        LocalDateTime pastExpiry = LocalDateTime.now().minusHours(1);
        DocumentShare share = new DocumentShare(doc, owner, recipientRahul, SharePermission.DOWNLOAD, pastExpiry);
        when(documentShareRepository.findByDocumentIdAndSharedWithEmail(100L, "rahul@securevault.com"))
                .thenReturn(Optional.of(share));

        assertFalse(documentAccessService.canView(doc, "rahul@securevault.com", false));
        assertFalse(documentAccessService.canDownload(doc, "rahul@securevault.com", false));
    }

    @Test
    public void testRevokeShareRemovesAccessImmediately() {
        when(documentRepository.findById(100L)).thenReturn(Optional.of(doc));
        DocumentShare share = new DocumentShare(doc, owner, recipientRahul, SharePermission.VIEW, null);
        share.setId(50L);
        when(documentShareRepository.findById(50L)).thenReturn(Optional.of(share));

        documentShareService.revokeShare(100L, 50L, "alice@securevault.com", false);

        verify(documentShareRepository).delete(share);
        verify(auditService).log(eq("alice@securevault.com"), eq("SHARE_REVOKE"), eq("DOCUMENT_SHARE"), eq(50L), anyString());
    }
}
