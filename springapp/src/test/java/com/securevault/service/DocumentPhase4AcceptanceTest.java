package com.securevault.service;

import com.securevault.dto.DocumentDTO;
import com.securevault.entity.Document;
import com.securevault.entity.DocumentVersion;
import com.securevault.entity.User;
import com.securevault.exception.BadRequestException;
import com.securevault.exception.ForbiddenException;
import com.securevault.repository.DocumentRepository;
import com.securevault.repository.DocumentVersionRepository;
import com.securevault.repository.UserRepository;
import com.securevault.util.FileValidationUtil;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
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
public class DocumentPhase4AcceptanceTest {

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private DocumentVersionRepository documentVersionRepository;

    @Mock
    private UserRepository userRepository;

    @TempDir
    Path tempStorageDir;

    private FileValidationUtil fileValidationUtil;
    private FileStorageService fileStorageService;
    private DocumentAccessService documentAccessService;
    private DocumentService documentService;

    private User userA;
    private User userB;
    private Document documentA;
    private DocumentVersion versionA;

    @BeforeEach
    void setUp() {
        fileValidationUtil = new FileValidationUtil(10 * 1024 * 1024L, 5 * 1024 * 1024L);
        fileStorageService = new FileStorageService(tempStorageDir.toString(), fileValidationUtil);
        fileStorageService.init();

        documentAccessService = new DocumentAccessService();
        documentService = new DocumentService(
                documentRepository,
                documentVersionRepository,
                userRepository,
                fileStorageService,
                documentAccessService
        );

        userA = new User("Alice", "alice@example.com", "passA");
        userA.setId(101L);

        userB = new User("Bob", "bob@example.com", "passB");
        userB.setId(102L);

        documentA = new Document("Contract", "contract.pdf", "LEGAL", "Confidential contract", userA);
        documentA.setId(201L);
        documentA.setDeleted(false);

        versionA = new DocumentVersion(documentA, 1, tempStorageDir.resolve("dummy.pdf").toString(),
                100L, "application/pdf", "dummyhash", userA, "v1");
        versionA.setId(301L);
    }

    @Test
    void testAcceptance_SpoofedFileIsRejected() {
        // Attacker creates a Windows executable (.exe) and renames it to statement.pdf
        byte[] exeBytes = new byte[]{
                'M', 'Z', (byte) 0x90, 0x00, 0x03, 0x00, 0x00, 0x00,
                0x04, 0x00, 0x00, 0x00, (byte) 0xFF, (byte) 0xFF, 0x00, 0x00
        };

        MockMultipartFile spoofedFile = new MockMultipartFile(
                "file", "statement.pdf", "application/pdf", exeBytes
        );

        BadRequestException ex = assertThrows(BadRequestException.class, () ->
                fileValidationUtil.validateFile(spoofedFile)
        );

        assertTrue(ex.getMessage().contains("Spoofed or invalid file detected"));
    }

    @Test
    void testAcceptance_UserCannotDownloadAnotherUsersDocument() {
        // User B attempts to download User A's document
        when(documentRepository.findByIdAndDeletedFalse(201L)).thenReturn(Optional.of(documentA));

        ForbiddenException ex = assertThrows(ForbiddenException.class, () ->
                documentService.getDocumentResourceWithAuth(201L, "bob@example.com", false)
        );

        assertEquals("You do not have permission to download this document", ex.getMessage());
    }

    @Test
    void testAcceptance_DeleteAppearsInTrashAndRestoreWorks() {
        // 1. Delete document -> soft delete
        when(documentRepository.findByIdAndDeletedFalse(201L)).thenReturn(Optional.of(documentA));
        when(documentRepository.save(any(Document.class))).thenAnswer(invocation -> invocation.getArgument(0));

        boolean deleted = documentService.deleteDocumentWithAuth(201L, "alice@example.com", false);
        assertTrue(deleted);
        assertTrue(documentA.isDeleted());
        assertNotNull(documentA.getDeletedAt());

        // 2. Query trash -> shows up in trash
        when(documentRepository.findByOwnerEmailAndDeletedTrue("alice@example.com"))
                .thenReturn(List.of(documentA));
        when(documentVersionRepository.findFirstByDocumentIdOrderByVersionNumberDesc(201L))
                .thenReturn(Optional.of(versionA));

        List<DocumentDTO> trashDocs = documentService.getTrashDocuments("alice@example.com", false);
        assertEquals(1, trashDocs.size());
        assertEquals("Contract", trashDocs.get(0).getTitle());
        assertTrue(trashDocs.get(0).isDeleted());

        // 3. Restore document
        when(documentRepository.findById(201L)).thenReturn(Optional.of(documentA));

        DocumentDTO restored = documentService.restoreDocument(201L, "alice@example.com", false);
        assertNotNull(restored);
        assertFalse(restored.isDeleted());
        assertNull(restored.getDeletedAt());
        assertFalse(documentA.isDeleted());
        assertNull(documentA.getDeletedAt());
    }

    @Test
    void testAcceptance_FileStoredUnderUuidNameNeverOriginalName() throws IOException {
        byte[] pdfBytes = "%PDF-1.5\n%\n1 0 obj\n<<>>\nendobj\ntrailer\n<<>>\n%%EOF".getBytes(StandardCharsets.US_ASCII);
        MockMultipartFile file = new MockMultipartFile(
                "file", "my-sensitive-passport-alice.pdf", "application/pdf", pdfBytes
        );

        FileStorageService.StoredFileResult result = fileStorageService.storeFile(file, 201L, 1);

        assertNotNull(result);
        assertNotNull(result.getStoredFileName());

        // Stored filename must NEVER be the user's uploaded name
        assertNotEquals("my-sensitive-passport-alice.pdf", result.getStoredFileName());

        // Stored filename must match UUID format: e.g. 123e4567-e89b-12d3-a456-426614174000.pdf
        Pattern uuidPattern = Pattern.compile("^[0-9a-fA-F\\-]{36}\\.pdf$");
        assertTrue(uuidPattern.matcher(result.getStoredFileName()).matches(),
                "Stored file name should be UUID format, but was: " + result.getStoredFileName());

        // Physical file must exist on disk in the version subdirectory
        Path storedFilePath = Path.of(result.getFilePath());
        assertTrue(Files.exists(storedFilePath));
        assertTrue(storedFilePath.toString().contains("201/1") || storedFilePath.toString().contains("201\\1"));
    }
}
