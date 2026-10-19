package com.securevault.service;

import com.securevault.dto.DocumentVersionDTO;
import com.securevault.entity.Document;
import com.securevault.entity.DocumentStatus;
import com.securevault.entity.DocumentVersion;
import com.securevault.entity.User;
import com.securevault.exception.BadRequestException;
import com.securevault.exception.ConflictException;
import com.securevault.repository.DocumentRepository;
import com.securevault.repository.DocumentVersionRepository;
import com.securevault.repository.UserRepository;
import com.securevault.util.FileValidationUtil;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.Resource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mock.web.MockMultipartFile;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class DocumentVersionControlTest {

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

    private User owner;
    private Document document;
    private List<DocumentVersion> versionStore;

    private static final byte[] PDF_V1_BYTES =
            "%PDF-1.5\n%\n1 0 obj\n<< /Version 1 >>\nendobj\ntrailer\n<<>>\n%%EOF".getBytes(StandardCharsets.US_ASCII);

    private static final byte[] PDF_V2_BYTES =
            "%PDF-1.5\n%\n1 0 obj\n<< /Version 2 >>\nendobj\ntrailer\n<<>>\n%%EOF".getBytes(StandardCharsets.US_ASCII);

    private static final byte[] PDF_V3_BYTES =
            "%PDF-1.5\n%\n1 0 obj\n<< /Version 3 >>\nendobj\ntrailer\n<<>>\n%%EOF".getBytes(StandardCharsets.US_ASCII);

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
                documentAccessService,
                fileValidationUtil
        );

        owner = new User("Alice", "alice@example.com", "pass");
        owner.setId(1L);

        document = new Document("Financial Report", "report.pdf", "FINANCIAL", "Q4 Report", owner);
        document.setId(100L);
        document.setCurrentVersion(1);
        document.setStatus(DocumentStatus.ACTIVE);
        document.setDeleted(false);

        versionStore = new ArrayList<>();

        // Store physical file for v1
        FileStorageService.StoredFileResult v1Stored = fileStorageService.storeFile(
                new MockMultipartFile("file", "report.pdf", "application/pdf", PDF_V1_BYTES),
                100L, 1
        );

        DocumentVersion v1 = new DocumentVersion(
                document, 1, v1Stored.getFilePath(), v1Stored.getFileSize(),
                v1Stored.getContentType(), v1Stored.getChecksum(), owner, "Initial v1"
        );
        v1.setId(1001L);
        versionStore.add(v1);

        // Lenient repository stubs
        lenient().when(documentRepository.findByIdAndDeletedFalse(100L)).thenReturn(Optional.of(document));
        lenient().when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(owner));
        lenient().when(documentRepository.save(any(Document.class))).thenAnswer(i -> i.getArgument(0));

        lenient().when(documentVersionRepository.findFirstByDocumentIdOrderByVersionNumberDesc(100L))
                .thenAnswer(i -> versionStore.stream().max(Comparator.comparingInt(DocumentVersion::getVersionNumber)));

        lenient().when(documentVersionRepository.findByDocumentIdOrderByVersionNumberDesc(100L))
                .thenAnswer(i -> versionStore.stream()
                        .sorted(Comparator.comparingInt(DocumentVersion::getVersionNumber).reversed())
                        .collect(Collectors.toList()));

        lenient().when(documentVersionRepository.save(any(DocumentVersion.class))).thenAnswer(i -> {
            DocumentVersion v = i.getArgument(0);
            if (v.getId() == null) {
                v.setId(1000L + versionStore.size() + 1);
            }
            versionStore.add(v);
            return v;
        });

        lenient().when(documentVersionRepository.findById(anyLong())).thenAnswer(i -> {
            Long id = i.getArgument(0);
            return versionStore.stream().filter(v -> v.getId().equals(id)).findFirst();
        });

        lenient().when(documentVersionRepository.findByDocumentIdAndVersionNumber(eq(100L), anyInt()))
                .thenAnswer(i -> {
                    Integer verNum = i.getArgument(1);
                    return versionStore.stream().filter(v -> v.getVersionNumber().equals(verNum)).findFirst();
                });
    }

    @Test
    void testVersionCreationOrder_v1_to_v2_to_v3() {
        assertEquals(1, versionStore.size());
        assertEquals(1, document.getCurrentVersion());

        // Upload v2
        MockMultipartFile fileV2 = new MockMultipartFile("file", "report.pdf", "application/pdf", PDF_V2_BYTES);
        DocumentVersionDTO v2DTO = documentService.createNewVersion(100L, fileV2, "Added Q4 tables", "alice@example.com", false);

        assertNotNull(v2DTO);
        assertEquals(2, v2DTO.getVersionNumber());
        assertEquals(2, document.getCurrentVersion());
        assertTrue(v2DTO.isCurrent());

        // Upload v3
        MockMultipartFile fileV3 = new MockMultipartFile("file", "report.pdf", "application/pdf", PDF_V3_BYTES);
        DocumentVersionDTO v3DTO = documentService.createNewVersion(100L, fileV3, "Executive summary update", "alice@example.com", false);

        assertNotNull(v3DTO);
        assertEquals(3, v3DTO.getVersionNumber());
        assertEquals(3, document.getCurrentVersion());
        assertTrue(v3DTO.isCurrent());

        // Retrieve versions list -> order must be newest first: v3, v2, v1
        List<DocumentVersionDTO> list = documentService.getVersionsForDocument(100L, "alice@example.com", false);
        assertEquals(3, list.size());
        assertEquals(3, list.get(0).getVersionNumber());
        assertTrue(list.get(0).isCurrent());
        assertEquals(2, list.get(1).getVersionNumber());
        assertFalse(list.get(1).isCurrent());
        assertEquals(1, list.get(2).getVersionNumber());
        assertFalse(list.get(2).isCurrent());
    }

    @Test
    void testDuplicateChecksum_IsRejected() {
        // Current version is v1 with PDF_V1_BYTES
        MockMultipartFile identicalFile = new MockMultipartFile("file", "report_copy.pdf", "application/pdf", PDF_V1_BYTES);

        BadRequestException ex = assertThrows(BadRequestException.class, () ->
                documentService.createNewVersion(100L, identicalFile, "Re-uploading same file", "alice@example.com", false)
        );

        assertTrue(ex.getMessage().contains("No changes detected"));
        assertEquals(1, versionStore.size());
        assertEquals(1, document.getCurrentVersion());
    }

    @Test
    void testRestoreCreatesV4AndKeepsV1toV3() {
        // Create v2 and v3
        MockMultipartFile fileV2 = new MockMultipartFile("file", "report.pdf", "application/pdf", PDF_V2_BYTES);
        documentService.createNewVersion(100L, fileV2, "v2 update", "alice@example.com", false);

        MockMultipartFile fileV3 = new MockMultipartFile("file", "report.pdf", "application/pdf", PDF_V3_BYTES);
        documentService.createNewVersion(100L, fileV3, "v3 update", "alice@example.com", false);

        assertEquals(3, versionStore.size());
        assertEquals(3, document.getCurrentVersion());

        // Restore version 1
        DocumentVersion v1 = versionStore.get(0);
        DocumentVersionDTO restoredDTO = documentService.restoreVersion(100L, v1.getId(), "alice@example.com", false);

        assertNotNull(restoredDTO);
        assertEquals(4, restoredDTO.getVersionNumber());
        assertEquals("Restored from v1", restoredDTO.getChangeDescription());
        assertEquals(4, document.getCurrentVersion());

        // Acceptance: all 4 versions exist in history (v1, v2, v3, v4)
        List<DocumentVersionDTO> history = documentService.getVersionsForDocument(100L, "alice@example.com", false);
        assertEquals(4, history.size());
        assertEquals(4, history.get(0).getVersionNumber()); // v4
        assertEquals(3, history.get(1).getVersionNumber()); // v3
        assertEquals(2, history.get(2).getVersionNumber()); // v2
        assertEquals(1, history.get(3).getVersionNumber()); // v1

        // Checksum of restored v4 must match v1
        assertEquals(v1.getChecksum(), restoredDTO.getChecksum());
    }

    @Test
    void testDownloadOfOldVersionReturnsExactFileWithChecksum() throws IOException {
        MockMultipartFile fileV2 = new MockMultipartFile("file", "report.pdf", "application/pdf", PDF_V2_BYTES);
        DocumentVersionDTO v2DTO = documentService.createNewVersion(100L, fileV2, "v2 update", "alice@example.com", false);

        DocumentVersion v1 = versionStore.get(0);
        Resource v1Resource = documentService.getVersionResource(100L, v1.getId(), "alice@example.com", false);
        byte[] v1DownloadedBytes = v1Resource.getInputStream().readAllBytes();
        assertArrayEquals(PDF_V1_BYTES, v1DownloadedBytes);
        assertEquals(v1.getChecksum(), FileValidationUtil.computeSha256(v1DownloadedBytes));

        Resource v2Resource = documentService.getVersionResource(100L, v2DTO.getId(), "alice@example.com", false);
        byte[] v2DownloadedBytes = v2Resource.getInputStream().readAllBytes();
        assertArrayEquals(PDF_V2_BYTES, v2DownloadedBytes);
        assertEquals(v2DTO.getChecksum(), FileValidationUtil.computeSha256(v2DownloadedBytes));
    }

    @Test
    void testApprovedDocumentStatusResetToPendingOnNewVersion() {
        document.setStatus(DocumentStatus.APPROVED);
        assertEquals(DocumentStatus.APPROVED, document.getStatus());

        MockMultipartFile fileV2 = new MockMultipartFile("file", "report.pdf", "application/pdf", PDF_V2_BYTES);
        documentService.createNewVersion(100L, fileV2, "new edits after approval", "alice@example.com", false);

        // Status must be reset to PENDING for re-verification
        assertEquals(DocumentStatus.PENDING, document.getStatus());
    }

    @Test
    void testConcurrentVersionCreation_ThrowsConflictException() {
        MockMultipartFile fileV2 = new MockMultipartFile("file", "report.pdf", "application/pdf", PDF_V2_BYTES);

        // Simulate DB unique constraint collision on (document_id, version_number)
        when(documentVersionRepository.save(any(DocumentVersion.class)))
                .thenThrow(new DataIntegrityViolationException("Duplicate entry '100-2' for key 'uk_doc_version'"));

        ConflictException ex = assertThrows(ConflictException.class, () ->
                documentService.createNewVersion(100L, fileV2, "concurrent upload", "alice@example.com", false)
        );

        assertTrue(ex.getMessage().contains("Concurrent version conflict detected"));
    }
}
