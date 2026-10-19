package com.securevault.service;

import com.securevault.dto.DocumentDTO;
import com.securevault.dto.UpdateDocumentRequest;
import com.securevault.entity.Document;
import com.securevault.entity.DocumentVersion;
import com.securevault.entity.User;
import com.securevault.exception.ForbiddenException;
import com.securevault.exception.ResourceNotFoundException;
import com.securevault.repository.DocumentRepository;
import com.securevault.repository.DocumentVersionRepository;
import com.securevault.repository.UserRepository;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.mock.web.MockMultipartFile;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class DocumentServiceTest {

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private DocumentVersionRepository documentVersionRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private FileStorageService fileStorageService;

    @Mock
    private DocumentAccessService documentAccessService;

    @Mock
    private AuditService auditService;

    @InjectMocks
    private DocumentService documentService;

    private User sampleUser;
    private Document sampleDoc;
    private DocumentVersion sampleVersion;

    @BeforeEach
    void setUp() {
        sampleUser = new User("bob", "bob@example.com", "pass");
        sampleUser.setId(10L);

        sampleDoc = new Document("passport.pdf", "passport.pdf", "ID", "My passport", sampleUser);
        sampleDoc.setId(100L);

        sampleVersion = new DocumentVersion(sampleDoc, 1, "/storage/uploads/doc_100_v1.pdf",
                1024L, "application/pdf", "dummychecksum", sampleUser, "Initial upload");
        sampleVersion.setId(500L);
    }

    @Test
    void testSaveDocument_Success() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "passport.pdf", "application/pdf", "PDF CONTENT".getBytes()
        );

        when(userRepository.findById(10L)).thenReturn(Optional.of(sampleUser));
        when(documentRepository.save(any(Document.class))).thenReturn(sampleDoc);
        when(fileStorageService.storeFile(any(), any(), any())).thenReturn(
                new FileStorageService.StoredFileResult("/storage/uploads/doc_100_v1.pdf", "doc_100_v1.pdf", 1024L, "application/pdf", "checksum123")
        );
        when(documentVersionRepository.save(any(DocumentVersion.class))).thenReturn(sampleVersion);
        when(documentVersionRepository.findFirstByDocumentIdOrderByVersionNumberDesc(100L))
                .thenReturn(Optional.of(sampleVersion));

        DocumentDTO result = documentService.saveDocument(file, 10L, 1L, "ID", "My passport");

        assertNotNull(result);
        assertEquals(100L, result.getId());
        assertEquals("passport.pdf", result.getTitle());
        verify(documentRepository, times(1)).save(any(Document.class));
        verify(documentVersionRepository, times(1)).save(any(DocumentVersion.class));
    }

    @Test
    void testGetAllDocuments_Success() {
        when(documentRepository.findAllByDeletedFalse()).thenReturn(List.of(sampleDoc));
        when(documentVersionRepository.findFirstByDocumentIdOrderByVersionNumberDesc(100L))
                .thenReturn(Optional.of(sampleVersion));

        List<DocumentDTO> docs = documentService.getAllDocuments();

        assertNotNull(docs);
        assertEquals(1, docs.size());
        assertEquals("passport.pdf", docs.get(0).getTitle());
    }

    @Test
    void testGetAllDocumentsForUser_Employee() {
        when(documentRepository.findByOwnerEmailAndDeletedFalse("bob@example.com")).thenReturn(List.of(sampleDoc));
        when(documentVersionRepository.findFirstByDocumentIdOrderByVersionNumberDesc(100L))
                .thenReturn(Optional.of(sampleVersion));

        List<DocumentDTO> docs = documentService.getAllDocumentsForUser("bob@example.com", false);

        assertNotNull(docs);
        assertEquals(1, docs.size());
    }

    @Test
    void testGetDocumentByIdWithAuth_Success() {
        when(documentRepository.findByIdAndDeletedFalse(100L)).thenReturn(Optional.of(sampleDoc));
        when(documentAccessService.canView(eq(sampleDoc), eq("bob@example.com"), eq(false), eq(false))).thenReturn(true);
        when(documentVersionRepository.findFirstByDocumentIdOrderByVersionNumberDesc(100L))
                .thenReturn(Optional.of(sampleVersion));

        DocumentDTO dto = documentService.getDocumentByIdWithAuth(100L, "bob@example.com", false, false);

        assertNotNull(dto);
        assertEquals(100L, dto.getId());
    }

    @Test
    void testGetDocumentByIdWithAuth_Forbidden() {
        when(documentRepository.findByIdAndDeletedFalse(100L)).thenReturn(Optional.of(sampleDoc));
        when(documentAccessService.canView(eq(sampleDoc), eq("intruder@example.com"), eq(false), eq(false))).thenReturn(false);

        assertThrows(ForbiddenException.class, () ->
                documentService.getDocumentByIdWithAuth(100L, "intruder@example.com", false, false)
        );
    }

    @Test
    void testGetDocumentResourceWithAuth_Success() {
        when(documentRepository.findByIdAndDeletedFalse(100L)).thenReturn(Optional.of(sampleDoc));
        when(documentAccessService.canDownload(eq(sampleDoc), eq("bob@example.com"), eq(false), eq(false))).thenReturn(true);
        when(documentVersionRepository.findFirstByDocumentIdOrderByVersionNumberDesc(100L)).thenReturn(Optional.of(sampleVersion));
        Resource mockResource = new ByteArrayResource("PDF".getBytes());
        when(fileStorageService.loadFileAsResource(sampleVersion.getFilePath())).thenReturn(mockResource);

        Resource resource = documentService.getDocumentResourceWithAuth(100L, "bob@example.com", false, false);

        assertNotNull(resource);
    }

    @Test
    void testUpdateDocumentDetails_Success() {
        when(documentRepository.findByIdAndDeletedFalse(100L)).thenReturn(Optional.of(sampleDoc));
        when(documentAccessService.canEdit(sampleDoc, "bob@example.com", false)).thenReturn(true);
        when(documentRepository.save(any(Document.class))).thenAnswer(inv -> inv.getArgument(0));
        when(documentVersionRepository.findFirstByDocumentIdOrderByVersionNumberDesc(100L)).thenReturn(Optional.of(sampleVersion));

        UpdateDocumentRequest req = new UpdateDocumentRequest("Updated Title", "LEGAL", "Updated desc", Collections.singleton("tag1"));
        DocumentDTO updated = documentService.updateDocumentDetails(100L, req, "bob@example.com", false);

        assertNotNull(updated);
        assertEquals("Updated Title", updated.getTitle());
    }

    @Test
    void testDeleteDocument_Success() {
        when(documentRepository.findByIdAndDeletedFalse(100L)).thenReturn(Optional.of(sampleDoc));
        when(documentAccessService.canDelete(sampleDoc, null, true)).thenReturn(true);

        boolean deleted = documentService.deleteDocument(100L);

        assertTrue(deleted);
        assertTrue(sampleDoc.isDeleted());
        assertNotNull(sampleDoc.getDeletedAt());
        verify(documentRepository, times(1)).save(sampleDoc);
    }

    @Test
    void testDeleteDocumentWithAuth_Success() {
        when(documentRepository.findByIdAndDeletedFalse(100L)).thenReturn(Optional.of(sampleDoc));
        when(documentAccessService.canDelete(sampleDoc, "bob@example.com", false)).thenReturn(true);

        documentService.deleteDocumentWithAuth(100L, "bob@example.com", false);

        assertTrue(sampleDoc.isDeleted());
        verify(documentRepository, times(1)).save(sampleDoc);
    }

    @Test
    void testDeleteDocumentWithAuth_Forbidden() {
        when(documentRepository.findByIdAndDeletedFalse(100L)).thenReturn(Optional.of(sampleDoc));
        when(documentAccessService.canDelete(sampleDoc, "intruder@example.com", false)).thenReturn(false);

        assertThrows(ForbiddenException.class, () ->
                documentService.deleteDocumentWithAuth(100L, "intruder@example.com", false)
        );
    }

    @Test
    void testRestoreDocument_Success() {
        sampleDoc.setDeleted(true);
        when(documentRepository.findById(100L)).thenReturn(Optional.of(sampleDoc));
        when(documentAccessService.canRestore(sampleDoc, "bob@example.com", false)).thenReturn(true);
        when(documentRepository.save(any(Document.class))).thenAnswer(inv -> inv.getArgument(0));
        when(documentVersionRepository.findFirstByDocumentIdOrderByVersionNumberDesc(100L)).thenReturn(Optional.of(sampleVersion));

        DocumentDTO restored = documentService.restoreDocument(100L, "bob@example.com", false);

        assertNotNull(restored);
        assertFalse(sampleDoc.isDeleted());
        assertNull(sampleDoc.getDeletedAt());
    }
}
