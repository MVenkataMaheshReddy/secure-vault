package com.securevault.service;

import com.securevault.dto.DocumentDTO;
import com.securevault.dto.PageResponse;
import com.securevault.dto.SearchResultDTO;
import com.securevault.entity.Document;
import com.securevault.entity.DocumentStatus;
import com.securevault.entity.DocumentVersion;
import com.securevault.entity.OcrStatus;
import com.securevault.entity.User;
import com.securevault.exception.ForbiddenException;
import com.securevault.repository.DocumentRepository;
import com.securevault.repository.DocumentVersionRepository;
import com.securevault.repository.UserRepository;
import com.securevault.service.ocr.OcrProcessingService;
import com.securevault.util.FileValidationUtil;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class DocumentSearchIntegrationTest {

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private DocumentVersionRepository documentVersionRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private OcrProcessingService ocrProcessingService;

    @TempDir
    Path tempStorageDir;

    private FileValidationUtil fileValidationUtil;
    private FileStorageService fileStorageService;
    private DocumentAccessService documentAccessService;
    private DocumentService documentService;

    private User userA;
    private User userB;
    private User adminUser;

    private Document docA;
    private DocumentVersion verA;

    @BeforeEach
    void setUp() {
        fileValidationUtil = new FileValidationUtil();
        fileStorageService = new FileStorageService(tempStorageDir.toString(), fileValidationUtil);
        documentAccessService = new DocumentAccessService();
        documentService = new DocumentService(
                documentRepository,
                documentVersionRepository,
                userRepository,
                fileStorageService,
                documentAccessService,
                fileValidationUtil,
                ocrProcessingService
        );

        userA = new User("Alice", "alice@securevault.com", "hash");
        userA.setId(1L);

        userB = new User("Bob", "bob@securevault.com", "hash");
        userB.setId(2L);

        adminUser = new User("Admin", "admin@securevault.com", "hash");
        adminUser.setId(3L);

        // Document owned by User A with OCR content containing "CGPA: 9.06"
        docA = new Document("Grade Card Semester 6", "gradesheet.pdf", "ACADEMICS", "Official university grade card", userA);
        docA.setId(101L);
        docA.setCurrentVersion(1);
        docA.setDeleted(false);

        verA = new DocumentVersion(
                docA,
                1,
                tempStorageDir.resolve("gradesheet.pdf").toString(),
                1024L,
                "application/pdf",
                "checksum906",
                userA,
                "v1"
        );
        verA.setId(1001L);
        verA.setOcrStatus(OcrStatus.DONE);
        verA.setOcrText("Transcript Details: Name: Alice, Registration: 20BCSE01, CGPA: 9.06, Distinction, Pass");
    }

    @Test
    void testSearchFindsDocumentByOcrContent906AndReturnsContentMatchSource() {
        when(documentRepository.searchDocumentsLike(eq("9.06"), any(), any(), any(), any(), any(), any()))
                .thenReturn(List.of(docA));
        when(documentVersionRepository.findByDocumentIdAndVersionNumber(101L, 1))
                .thenReturn(Optional.of(verA));

        PageResponse<SearchResultDTO> response = documentService.searchDocuments(
                "9.06", null, null, null, null, null, null, 0, 10, "alice@securevault.com", false
        );

        assertNotNull(response);
        assertEquals(1, response.getContent().size());
        SearchResultDTO result = response.getContent().get(0);
        assertEquals(101L, result.getId());
        assertEquals("content", result.getMatchSource());
        assertTrue(result.getSnippet().contains("9.06"));
        assertEquals(OcrStatus.DONE, result.getOcrStatus());
    }

    @Test
    void testUserBCannotFindUserAPrivateDocumentViaSearch() {
        when(documentRepository.searchDocumentsLike(eq("9.06"), any(), any(), any(), any(), any(), any()))
                .thenReturn(List.of(docA));

        // User B searches for "9.06"
        PageResponse<SearchResultDTO> response = documentService.searchDocuments(
                "9.06", null, null, null, null, null, null, 0, 10, "bob@securevault.com", false
        );

        assertNotNull(response);
        assertEquals(0, response.getContent().size(), "User B must not see User A's private document in search results");
    }

    @Test
    void testAdminCanFindUserADocumentViaSearch() {
        when(documentRepository.searchDocumentsLike(eq("9.06"), any(), any(), any(), any(), any(), any()))
                .thenReturn(List.of(docA));
        when(documentVersionRepository.findByDocumentIdAndVersionNumber(101L, 1))
                .thenReturn(Optional.of(verA));

        // Admin searches for "9.06"
        PageResponse<SearchResultDTO> response = documentService.searchDocuments(
                "9.06", null, null, null, null, null, null, 0, 10, "admin@securevault.com", true
        );

        assertNotNull(response);
        assertEquals(1, response.getContent().size());
        assertEquals(101L, response.getContent().get(0).getId());
    }

    @Test
    void testSoftDeletedDocumentNeverReturnedInSearch() {
        docA.setDeleted(true);
        when(documentRepository.searchDocumentsLike(eq("9.06"), any(), any(), any(), any(), any(), any()))
                .thenReturn(List.of(docA));

        PageResponse<SearchResultDTO> response = documentService.searchDocuments(
                "9.06", null, null, null, null, null, null, 0, 10, "alice@securevault.com", false
        );

        assertEquals(0, response.getContent().size(), "Soft-deleted documents must NEVER be returned in search");
    }

    @Test
    void testMetadataSearchReturnsMetadataMatchSource() {
        when(documentRepository.searchDocumentsLike(eq("Semester"), any(), any(), any(), any(), any(), any()))
                .thenReturn(List.of(docA));
        when(documentVersionRepository.findByDocumentIdAndVersionNumber(101L, 1))
                .thenReturn(Optional.of(verA));

        PageResponse<SearchResultDTO> response = documentService.searchDocuments(
                "Semester", null, null, null, null, null, null, 0, 10, "alice@securevault.com", false
        );

        assertNotNull(response);
        assertEquals(1, response.getContent().size());
        SearchResultDTO result = response.getContent().get(0);
        assertEquals("metadata", result.getMatchSource());
    }

    @Test
    void testRetryOcrByOwnerSucceedsAndSetsStatusPending() {
        when(documentRepository.findByIdAndDeletedFalse(101L)).thenReturn(Optional.of(docA));
        when(documentVersionRepository.findByDocumentIdAndVersionNumber(101L, 1)).thenReturn(Optional.of(verA));
        when(documentVersionRepository.save(any(DocumentVersion.class))).thenAnswer(inv -> inv.getArgument(0));
        when(documentVersionRepository.findFirstByDocumentIdOrderByVersionNumberDesc(101L)).thenReturn(Optional.of(verA));

        DocumentDTO updated = documentService.retryOcr(101L, "alice@securevault.com", false);

        assertNotNull(updated);
        assertEquals(OcrStatus.PENDING, verA.getOcrStatus());
        verify(ocrProcessingService).processOcrAsync(verA.getId());
    }

    @Test
    void testRetryOcrByNonOwnerThrowsForbidden() {
        when(documentRepository.findByIdAndDeletedFalse(101L)).thenReturn(Optional.of(docA));

        assertThrows(ForbiddenException.class, () -> {
            documentService.retryOcr(101L, "bob@securevault.com", false);
        });
        verify(ocrProcessingService, never()).processOcrAsync(any());
    }

    @Test
    void testUploadReturnsQuicklyWithOcrPendingWithoutWaitingForOcr() {
        byte[] pdfContent = "%PDF-1.5 minimal valid pdf".getBytes();
        MockMultipartFile file = new MockMultipartFile(
                "file", "doc.pdf", "application/pdf", pdfContent
        );

        when(userRepository.findByEmail("alice@securevault.com")).thenReturn(Optional.of(userA));
        when(documentRepository.save(any(Document.class))).thenAnswer(inv -> {
            Document d = inv.getArgument(0);
            d.setId(200L);
            return d;
        });
        when(documentVersionRepository.save(any(DocumentVersion.class))).thenAnswer(inv -> {
            DocumentVersion v = inv.getArgument(0);
            v.setId(2001L);
            return v;
        });
        when(documentVersionRepository.findFirstByDocumentIdOrderByVersionNumberDesc(200L)).thenAnswer(inv -> {
            DocumentVersion v = new DocumentVersion();
            v.setOcrStatus(OcrStatus.PENDING);
            return Optional.of(v);
        });

        long startTime = System.currentTimeMillis();
        DocumentDTO result = documentService.createDocument(file, "Test", "DOCS", "Desc", null, "alice@securevault.com");
        long duration = System.currentTimeMillis() - startTime;

        assertNotNull(result);
        assertEquals(OcrStatus.PENDING, result.getOcrStatus());
        assertTrue(duration < 1000, "Upload must return immediately without waiting for OCR");
        verify(ocrProcessingService).processOcrAsync(2001L);
    }
}
