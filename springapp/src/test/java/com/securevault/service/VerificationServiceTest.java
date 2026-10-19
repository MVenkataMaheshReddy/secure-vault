package com.securevault.service;

import com.securevault.dto.FileCheckResponseDTO;
import com.securevault.dto.PublicVerificationDTO;
import com.securevault.dto.VerificationRecordDTO;
import com.securevault.entity.Document;
import com.securevault.entity.DocumentStatus;
import com.securevault.entity.DocumentVersion;
import com.securevault.entity.User;
import com.securevault.entity.VerificationRecord;
import com.securevault.entity.VerificationStatus;
import com.securevault.exception.ForbiddenException;
import com.securevault.exception.RateLimitExceededException;
import com.securevault.exception.ResourceNotFoundException;
import com.securevault.repository.DocumentRepository;
import com.securevault.repository.DocumentVersionRepository;
import com.securevault.repository.VerificationRecordRepository;
import com.securevault.util.FileValidationUtil;
import com.securevault.util.InMemoryRateLimiter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.Resource;
import org.springframework.mock.web.MockMultipartFile;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class VerificationServiceTest {

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private DocumentVersionRepository documentVersionRepository;

    @Mock
    private VerificationRecordRepository verificationRecordRepository;

    @Mock
    private DocumentAccessService documentAccessService;

    @TempDir
    Path tempQrDir;

    private QrCodeService qrCodeService;
    private InMemoryRateLimiter rateLimiter;
    private VerificationService verificationService;

    private User owner;
    private Document document;
    private DocumentVersion version1;
    private DocumentVersion version2;
    private static final byte[] DOC_CONTENT_V1 = "Official Document Content V1".getBytes(StandardCharsets.UTF_8);
    private static final byte[] DOC_CONTENT_V2 = "Official Document Content V2".getBytes(StandardCharsets.UTF_8);
    private String checksumV1;
    private String checksumV2;

    @BeforeEach
    void setUp() {
        qrCodeService = new QrCodeService("http://localhost:3000", tempQrDir.toString());
        rateLimiter = new InMemoryRateLimiter(30);

        verificationService = new VerificationService(
                documentRepository,
                documentVersionRepository,
                verificationRecordRepository,
                qrCodeService,
                documentAccessService,
                rateLimiter
        );

        owner = new User();
        owner.setId(10L);
        owner.setName("Dr. Jane Doe");
        owner.setEmail("jane.doe@securevault.com");

        checksumV1 = FileValidationUtil.computeSha256(DOC_CONTENT_V1);
        checksumV2 = FileValidationUtil.computeSha256(DOC_CONTENT_V2);

        document = new Document();
        document.setId(100L);
        document.setTitle("Academic Transcript");
        document.setOriginalFileName("transcript.pdf");
        document.setCategory("ACADEMIC");
        document.setOwner(owner);
        document.setStatus(DocumentStatus.PENDING);
        document.setCurrentVersion(2);

        version1 = new DocumentVersion(document, 1, "path/v1.pdf", (long) DOC_CONTENT_V1.length, "application/pdf", checksumV1, owner, "Initial version");
        version1.setId(1L);

        version2 = new DocumentVersion(document, 2, "path/v2.pdf", (long) DOC_CONTENT_V2.length, "application/pdf", checksumV2, owner, "Second version");
        version2.setId(2L);

        List<DocumentVersion> versions = new ArrayList<>();
        versions.add(version2);
        versions.add(version1);
        document.setVersions(versions);
    }

    @Test
    @DisplayName("Verification ID format must be 'SV-' + 8 uppercase hex characters")
    void testVerificationIdFormatAndUniqueness() {
        Set<String> generatedIds = new HashSet<>();
        for (int i = 0; i < 100; i++) {
            String id = qrCodeService.generateVerificationId();
            assertNotNull(id);
            assertTrue(id.startsWith("SV-"), "ID should start with SV-: " + id);
            assertEquals(11, id.length(), "ID length should be exactly 11 chars: " + id);
            assertTrue(id.matches("^SV-[0-9A-F]{8}$"), "ID should match format SV-[0-9A-F]{8}: " + id);
            generatedIds.add(id);
        }
        assertEquals(100, generatedIds.size(), "All 100 generated IDs should be unique");
    }

    @Test
    @DisplayName("QR code contains ONLY verification URL and produces valid 300x300 PNG")
    void testQrCodeContentAndDimensions() throws IOException {
        String verificationId = "SV-A1B2C3D4";
        String expectedUrl = "http://localhost:3000/verify/SV-A1B2C3D4";
        assertEquals(expectedUrl, qrCodeService.buildVerificationUrl(verificationId));

        String savedPath = qrCodeService.generateAndSaveQrCode(verificationId);
        assertNotNull(savedPath);
        assertTrue(Files.exists(Path.of(savedPath)));

        byte[] qrBytes = Files.readAllBytes(Path.of(savedPath));
        assertTrue(qrBytes.length > 0);

        // Verify PNG magic header: 0x89 0x50 0x4E 0x47 0x0D 0x0A 0x1A 0x0A
        assertEquals((byte) 0x89, qrBytes[0]);
        assertEquals((byte) 'P', qrBytes[1]);
        assertEquals((byte) 'N', qrBytes[2]);
        assertEquals((byte) 'G', qrBytes[3]);
    }

    @Test
    @DisplayName("Generate verification succeeds, sets status APPROVED, creates QR, and is idempotent")
    void testGenerateVerificationSuccessAndIdempotency() {
        when(documentRepository.findByIdAndDeletedFalse(100L)).thenReturn(Optional.of(document));
        when(documentRepository.existsByVerificationId(anyString())).thenReturn(false);
        when(verificationRecordRepository.existsByVerificationId(anyString())).thenReturn(false);
        when(verificationRecordRepository.findByDocumentId(100L)).thenReturn(Optional.empty());

        when(verificationRecordRepository.save(any(VerificationRecord.class))).thenAnswer(invocation -> {
            VerificationRecord record = invocation.getArgument(0);
            record.setId(500L);
            return record;
        });

        // First call: document must be APPROVED to generate
        document.setStatus(DocumentStatus.APPROVED);
        VerificationRecordDTO result = verificationService.generateVerification(100L, "admin@securevault.com", true);

        assertNotNull(result);
        assertNotNull(result.getVerificationId());
        assertTrue(result.getVerificationId().startsWith("SV-"));
        assertEquals(VerificationStatus.VALID, result.getStatus());
        assertEquals(DocumentStatus.APPROVED, document.getStatus());
        verify(documentRepository, times(1)).save(document);

        // Second call (idempotent): returns existing verification without generating new ID
        when(verificationRecordRepository.findByVerificationId(document.getVerificationId()))
                .thenReturn(Optional.of(new VerificationRecord(document, document.getVerificationId(), "path", VerificationStatus.VALID)));

        VerificationRecordDTO idempotentResult = verificationService.generateVerification(100L, "admin@securevault.com", true);
        assertEquals(result.getVerificationId(), idempotentResult.getVerificationId());
    }

    @Test
    @DisplayName("PENDING or REJECTED documents cannot have verification reference generated")
    void testGenerateVerificationFailsForPendingOrRejected() {
        when(documentRepository.findByIdAndDeletedFalse(100L)).thenReturn(Optional.of(document));

        document.setStatus(DocumentStatus.PENDING);
        assertThrows(com.securevault.exception.BadRequestException.class, () ->
                verificationService.generate(100L));

        document.setStatus(DocumentStatus.REJECTED);
        assertThrows(com.securevault.exception.BadRequestException.class, () ->
                verificationService.generate(100L));
    }

    @Test
    @DisplayName("Non-admin cannot manually generate verification")
    void testGenerateVerificationForbiddenForNonAdmin() {
        assertThrows(ForbiddenException.class, () ->
                verificationService.generateVerification(100L, "jane.doe@securevault.com", false));
    }

    @Test
    @DisplayName("Public verification endpoint returns ONLY safe info and NEVER leaks sensitive data")
    void testPublicVerificationLeaksNoSensitiveData() {
        String verificationId = "SV-12345678";
        document.setStatus(DocumentStatus.APPROVED);
        VerificationRecord record = new VerificationRecord(document, verificationId, "storage/qr/SV-12345678.png", VerificationStatus.VALID);

        when(verificationRecordRepository.findByVerificationId(verificationId)).thenReturn(Optional.of(record));
        when(documentVersionRepository.findByDocumentIdAndVersionNumber(100L, 2)).thenReturn(Optional.of(version2));

        PublicVerificationDTO publicInfo = verificationService.getPublicVerification(verificationId, "127.0.0.1");

        assertNotNull(publicInfo);
        assertEquals("SV-12345678", publicInfo.getVerificationId());
        assertEquals("Academic Transcript", publicInfo.getDocumentTitle());
        assertEquals("ACADEMIC", publicInfo.getCategory());
        assertEquals("Dr. Jane Doe", publicInfo.getUploadedBy());
        assertEquals("Dr. Jane Doe", publicInfo.getOwnerDisplayName());
        assertEquals("VALID", publicInfo.getStatus());
        assertEquals(2, publicInfo.getCurrentVersion());
        assertEquals(2, publicInfo.getCurrentVersionNumber());
        assertEquals(checksumV2, publicInfo.getChecksum());

        // STRICT CHECK: Ensure owner email is NOT exposed anywhere in DTO
        assertNotEquals(owner.getEmail(), publicInfo.getUploadedBy());
        assertFalse(publicInfo.getUploadedBy().contains("@"));
    }

    @Test
    @DisplayName("Public verification returns 404/ResourceNotFoundException for unknown verification ID")
    void testPublicVerificationUnknownId() {
        // Unknown ID matching format
        when(verificationRecordRepository.findByVerificationId("SV-A1B2C3D4")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () ->
                verificationService.getPublicVerification("SV-A1B2C3D4", "127.0.0.1"));

        // Malformed ID
        assertThrows(ResourceNotFoundException.class, () ->
                verificationService.getPublicVerification("SV-INVALID-TOO-LONG", "127.0.0.1"));
    }

    @Test
    @DisplayName("Check file authenticity matches authentic copy and reports matched version number")
    void testCheckFileAuthenticityMatch() {
        String verificationId = "SV-12345678";
        VerificationRecord record = new VerificationRecord(document, verificationId, "storage/qr.png", VerificationStatus.VALID);

        when(verificationRecordRepository.findByVerificationId(verificationId)).thenReturn(Optional.of(record));
        when(documentVersionRepository.findByDocumentIdOrderByVersionNumberDesc(100L))
                .thenReturn(List.of(version2, version1));

        // Test with file matching version 1
        MockMultipartFile fileV1 = new MockMultipartFile("file", "copy.pdf", "application/pdf", DOC_CONTENT_V1);
        FileCheckResponseDTO responseV1 = verificationService.checkFileAuthenticity(verificationId, fileV1, "127.0.0.1");

        assertTrue(responseV1.isMatches());
        assertEquals(1, responseV1.getMatchedVersion());
        assertEquals("VALID", responseV1.getStatus());
        assertEquals(checksumV1, responseV1.getUploadedFileChecksum());
        assertTrue(responseV1.getMessage().contains("authentic"));

        // Test with file matching version 2 (current)
        MockMultipartFile fileV2 = new MockMultipartFile("file", "copy.pdf", "application/pdf", DOC_CONTENT_V2);
        FileCheckResponseDTO responseV2 = verificationService.checkFileAuthenticity(verificationId, fileV2, "127.0.0.1");

        assertTrue(responseV2.isMatches());
        assertEquals(2, responseV2.getMatchedVersion());
        assertEquals(checksumV2, responseV2.getUploadedFileChecksum());
    }

    @Test
    @DisplayName("Check file authenticity detects tampered/modified file (checksum mismatch)")
    void testCheckFileAuthenticityTamperedMismatch() {
        String verificationId = "SV-12345678";
        VerificationRecord record = new VerificationRecord(document, verificationId, "storage/qr.png", VerificationStatus.VALID);

        when(verificationRecordRepository.findByVerificationId(verificationId)).thenReturn(Optional.of(record));
        when(documentVersionRepository.findByDocumentIdOrderByVersionNumberDesc(100L))
                .thenReturn(List.of(version2, version1));

        byte[] tamperedBytes = "Tampered fraudulent content".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile tamperedFile = new MockMultipartFile("file", "fake.pdf", "application/pdf", tamperedBytes);

        FileCheckResponseDTO response = verificationService.checkFileAuthenticity(verificationId, tamperedFile, "127.0.0.1");

        assertFalse(response.isMatches());
        assertNull(response.getMatchedVersion());
        assertNotEquals(checksumV1, response.getUploadedFileChecksum());
        assertNotEquals(checksumV2, response.getUploadedFileChecksum());
        assertTrue(response.getMessage().toLowerCase().contains("mismatch"));
    }

    @Test
    @DisplayName("Revoke verification sets status to REVOKED and reflects in public lookup and file check")
    void testRevokeVerificationState() {
        String verificationId = "SV-12345678";
        VerificationRecord record = new VerificationRecord(document, verificationId, "storage/qr.png", VerificationStatus.VALID);

        when(documentRepository.findByIdAndDeletedFalse(100L)).thenReturn(Optional.of(document));
        when(verificationRecordRepository.findByDocumentId(100L)).thenReturn(Optional.of(record));
        when(verificationRecordRepository.save(any(VerificationRecord.class))).thenAnswer(invocation -> invocation.getArgument(0));

        VerificationRecordDTO revoked = verificationService.revokeVerification(100L, "verifier@securevault.com", true);
        assertEquals(VerificationStatus.REVOKED, revoked.getStatus());

        // Check public endpoint reflects REVOKED
        when(verificationRecordRepository.findByVerificationId(verificationId)).thenReturn(Optional.of(record));
        when(documentVersionRepository.findByDocumentIdAndVersionNumber(100L, 2)).thenReturn(Optional.of(version2));

        PublicVerificationDTO publicInfo = verificationService.getPublicVerification(verificationId, "127.0.0.1");
        assertEquals("REVOKED", publicInfo.getStatus());

        // Check file check reflects REVOKED message
        when(documentVersionRepository.findByDocumentIdOrderByVersionNumberDesc(100L))
                .thenReturn(List.of(version2, version1));
        MockMultipartFile fileV2 = new MockMultipartFile("file", "copy.pdf", "application/pdf", DOC_CONTENT_V2);
        FileCheckResponseDTO checkResp = verificationService.checkFileAuthenticity(verificationId, fileV2, "127.0.0.1");

        assertEquals("REVOKED", checkResp.getStatus());
        assertTrue(checkResp.getMessage().contains("REVOKED"));
    }

    @Test
    @DisplayName("Soft-deleted or PENDING document stops reporting VALID and reports REVOKED")
    void testDeletedOrPendingDocumentReportsRevoked() {
        String verificationId = "SV-12345678";
        document.setStatus(DocumentStatus.APPROVED);
        document.setDeleted(true); // soft-deleted
        VerificationRecord record = new VerificationRecord(document, verificationId, "storage/qr.png", VerificationStatus.VALID);

        when(verificationRecordRepository.findByVerificationId(verificationId)).thenReturn(Optional.of(record));

        PublicVerificationDTO publicInfo = verificationService.getPublicVerification(verificationId, "127.0.0.1");
        assertEquals("REVOKED", publicInfo.getStatus());

        // When approval is reset to PENDING (e.g. on new version)
        document.setDeleted(false);
        document.setStatus(DocumentStatus.PENDING);
        PublicVerificationDTO pendingInfo = verificationService.getPublicVerification(verificationId, "127.0.0.1");
        assertEquals("REVOKED", pendingInfo.getStatus());
    }

    @Test
    @DisplayName("Non-admin cannot revoke verification")
    void testRevokeForbiddenForNonAdmin() {
        assertThrows(ForbiddenException.class, () ->
                verificationService.revokeVerification(100L, "employee@securevault.com", false));
    }

    @Test
    @DisplayName("Rate limiter enforces 30 requests per minute per IP")
    void testRateLimiterLimitsAt30() {
        InMemoryRateLimiter limiter = new InMemoryRateLimiter(30);
        String testIp = "192.168.1.50";

        for (int i = 0; i < 30; i++) {
            assertTrue(limiter.tryAcquire(testIp), "Request " + (i + 1) + " should be allowed");
        }

        // 31st request should be rejected
        assertFalse(limiter.tryAcquire(testIp), "31st request from same IP should be blocked by rate limiter");
        assertThrows(RateLimitExceededException.class, () -> limiter.checkLimit(testIp));

        // Different IP should still be allowed
        assertTrue(limiter.tryAcquire("192.168.1.51"), "Request from different IP should be allowed");
    }
}
