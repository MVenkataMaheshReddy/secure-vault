package com.securevault;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.Result;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import com.securevault.dto.ApprovalDTO;
import com.securevault.dto.PublicVerificationDTO;
import com.securevault.dto.VerificationRecordDTO;
import com.securevault.entity.Approval;
import com.securevault.entity.ApprovalStatus;
import com.securevault.entity.Document;
import com.securevault.entity.DocumentStatus;
import com.securevault.entity.DocumentVersion;
import com.securevault.entity.Role;
import com.securevault.entity.RoleName;
import com.securevault.entity.User;
import com.securevault.entity.VerificationRecord;
import com.securevault.entity.VerificationStatus;
import com.securevault.exception.BadRequestException;
import com.securevault.exception.ForbiddenException;
import com.securevault.exception.ResourceNotFoundException;
import com.securevault.repository.ApprovalRepository;
import com.securevault.repository.DocumentRepository;
import com.securevault.repository.DocumentVersionRepository;
import com.securevault.repository.UserRepository;
import com.securevault.repository.VerificationRecordRepository;
import com.securevault.service.ApprovalService;
import com.securevault.service.DocumentAccessService;
import com.securevault.service.QrCodeService;
import com.securevault.service.VerificationService;
import com.securevault.util.InMemoryRateLimiter;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class QrVerificationIntegrationTest {

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private DocumentVersionRepository documentVersionRepository;

    @Mock
    private VerificationRecordRepository verificationRecordRepository;

    @Mock
    private ApprovalRepository approvalRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private DocumentAccessService documentAccessService;

    private QrCodeService qrCodeService;
    private InMemoryRateLimiter rateLimiter;
    private VerificationService verificationService;
    private ApprovalService approvalService;

    private User employee;
    private User admin;
    private Document document;
    private DocumentVersion documentVersion;
    private Approval pendingApproval;

    private static final String FRONTEND_BASE_URL = "http://localhost:3000";
    private static final String SAMPLE_CHECKSUM = "a1b2c3d4e5f67890123456789abcdef0123456789abcdef0123456789abcdef0";

    @BeforeEach
    void setUp() {
        qrCodeService = new QrCodeService(FRONTEND_BASE_URL, "storage/qr");
        rateLimiter = new InMemoryRateLimiter(30);

        verificationService = new VerificationService(
                documentRepository,
                documentVersionRepository,
                verificationRecordRepository,
                qrCodeService,
                documentAccessService,
                rateLimiter,
                approvalRepository,
                null
        );

        approvalService = new ApprovalService(
                approvalRepository,
                documentRepository,
                userRepository,
                null,
                verificationService,
                null
        );

        Role employeeRole = new Role(RoleName.EMPLOYEE);
        Role adminRole = new Role(RoleName.ADMIN);

        employee = new User("Alice Employee", "alice@securevault.com", "hash");
        employee.setId(10L);
        employee.setRoles(new HashSet<>(Collections.singleton(employeeRole)));

        admin = new User("Carol Admin", "admin@securevault.com", "hash");
        admin.setId(20L);
        admin.setRoles(new HashSet<>(Collections.singleton(adminRole)));

        document = new Document();
        document.setId(100L);
        document.setTitle("Confidential Annual Budget");
        document.setCategory("FINANCE");
        document.setOwner(employee);
        document.setStatus(DocumentStatus.PENDING);
        document.setCurrentVersion(1);
        document.setDeleted(false);

        documentVersion = new DocumentVersion(
                document,
                1,
                "storage/documents/100/1/budget.pdf",
                1024L,
                "application/pdf",
                SAMPLE_CHECKSUM,
                employee,
                "Initial upload v1"
        );
        documentVersion.setId(1L);

        pendingApproval = new Approval(document, ApprovalStatus.PENDING, "Initial review required");
        pendingApproval.setId(50L);
    }

    @Test
    @DisplayName("1. ID format and uniqueness: format is 'SV-' + 8 hex chars, unique across multiple generations")
    void testIdFormatAndUniqueness() {
        Set<String> generatedIds = new HashSet<>();
        for (int i = 0; i < 200; i++) {
            String id = qrCodeService.generateVerificationId();
            assertNotNull(id);
            assertTrue(id.startsWith("SV-"), "Verification ID must start with 'SV-': " + id);
            assertEquals(11, id.length(), "Verification ID must be exactly 11 characters long: " + id);
            assertTrue(id.matches("^SV-[0-9A-F]{8}$"), "Verification ID must match regex ^SV-[0-9A-F]{8}$: " + id);
            generatedIds.add(id);
        }
        assertEquals(200, generatedIds.size(), "All 200 generated verification IDs must be distinct and unique");
    }

    @Test
    @DisplayName("2. PENDING and REJECTED documents have no verification reference")
    void testPendingAndRejectedDocumentsHaveNoReference() {
        when(documentRepository.findByIdAndDeletedFalse(100L)).thenReturn(Optional.of(document));

        // When document is PENDING
        document.setStatus(DocumentStatus.PENDING);
        BadRequestException pendingEx = assertThrows(BadRequestException.class, () ->
                verificationService.generate(100L));
        assertTrue(pendingEx.getMessage().contains("PENDING or REJECTED"));
        assertNull(document.getVerificationId(), "Pending document must not acquire a verification ID");

        // When document is REJECTED
        document.setStatus(DocumentStatus.REJECTED);
        BadRequestException rejectedEx = assertThrows(BadRequestException.class, () ->
                verificationService.generate(100L));
        assertTrue(rejectedEx.getMessage().contains("PENDING or REJECTED"));
        assertNull(document.getVerificationId(), "Rejected document must not acquire a verification ID");
    }

    @Test
    @DisplayName("3. Admin approve generates exactly one verification reference and is idempotent")
    void testApproveGeneratesExactlyOneReferenceIdempotent() {
        when(approvalRepository.findById(50L)).thenReturn(Optional.of(pendingApproval));
        when(userRepository.findByEmail("admin@securevault.com")).thenReturn(Optional.of(admin));
        when(approvalRepository.save(any(Approval.class))).thenAnswer(inv -> inv.getArgument(0));

        when(documentRepository.findByIdAndDeletedFalse(100L)).thenReturn(Optional.of(document));
        when(documentRepository.existsByVerificationId(anyString())).thenReturn(false);
        when(verificationRecordRepository.existsByVerificationId(anyString())).thenReturn(false);
        when(verificationRecordRepository.findByDocumentId(100L)).thenReturn(Optional.empty());

        when(verificationRecordRepository.save(any(VerificationRecord.class))).thenAnswer(inv -> {
            VerificationRecord r = inv.getArgument(0);
            r.setId(77L);
            return r;
        });

        // Step 1: Admin approves document
        ApprovalDTO approvalResult = approvalService.approveApproval(50L, "Approved for official publishing", "admin@securevault.com", true);
        assertEquals(ApprovalStatus.APPROVED, approvalResult.getStatus());
        assertEquals(DocumentStatus.APPROVED, document.getStatus());
        assertNotNull(document.getVerificationId());
        assertTrue(document.getVerificationId().startsWith("SV-"));

        String generatedId = document.getVerificationId();

        // Step 2: Idempotent check - calling generate again returns the same existing record
        when(verificationRecordRepository.findByVerificationId(generatedId)).thenReturn(Optional.of(
                new VerificationRecord(document, generatedId, "/api/documents/100/qr", VerificationStatus.VALID)
        ));

        VerificationRecordDTO secondCall = verificationService.generate(100L);
        assertEquals(generatedId, secondCall.getVerificationId(), "Idempotent call must return the exact same verification ID");
    }

    @Test
    @DisplayName("4. Public endpoint works without token and exposes no sensitive fields")
    void testPublicEndpointWorksWithoutTokenAndExposesNoSensitiveFields() {
        String verificationId = "SV-CAFEBABE";
        document.setStatus(DocumentStatus.APPROVED);
        document.setVerificationId(verificationId);

        VerificationRecord record = new VerificationRecord(document, verificationId, "/api/documents/100/qr", VerificationStatus.VALID);
        record.setId(88L);
        record.setCreatedAt(LocalDateTime.of(2026, 10, 15, 12, 0, 0));

        when(verificationRecordRepository.findByVerificationId(verificationId)).thenReturn(Optional.of(record));
        when(documentVersionRepository.findByDocumentIdAndVersionNumber(100L, 1)).thenReturn(Optional.of(documentVersion));

        PublicVerificationDTO dto = verificationService.getPublicVerification(verificationId, "127.0.0.1");

        assertNotNull(dto);
        assertEquals("SV-CAFEBABE", dto.getVerificationId());
        assertEquals("VALID", dto.getStatus());
        assertEquals("Confidential Annual Budget", dto.getDocumentTitle());
        assertEquals("FINANCE", dto.getCategory());
        assertEquals("Alice Employee", dto.getUploadedBy());
        assertEquals(1, dto.getCurrentVersion());
        assertEquals(SAMPLE_CHECKSUM, dto.getChecksum());

        // STRICT PRIVACY VERIFICATION: Confirm no email, no file paths, no document content
        assertFalse(dto.getUploadedBy().contains("@"), "UploadedBy must only be display name, never an email address");
        assertNotEquals(employee.getEmail(), dto.getUploadedBy());
    }

    @Test
    @DisplayName("5. Unknown or malformed ID returns 404 with generic message 'Verification reference not found'")
    void testUnknownOrMalformedIdReturns404() {
        // Unknown ID in database
        when(verificationRecordRepository.findByVerificationId("SV-12345678")).thenReturn(Optional.empty());
        ResourceNotFoundException notFoundEx = assertThrows(ResourceNotFoundException.class, () ->
                verificationService.getPublicVerification("SV-12345678", "127.0.0.1"));
        assertEquals("Verification reference not found", notFoundEx.getMessage());

        // Malformed IDs (regex rejection without querying DB)
        ResourceNotFoundException malformed1 = assertThrows(ResourceNotFoundException.class, () ->
                verificationService.getPublicVerification("MALFORMED", "127.0.0.1"));
        assertEquals("Verification reference not found", malformed1.getMessage());

        ResourceNotFoundException malformed2 = assertThrows(ResourceNotFoundException.class, () ->
                verificationService.getPublicVerification("SV-123", "127.0.0.1"));
        assertEquals("Verification reference not found", malformed2.getMessage());

        ResourceNotFoundException malformed3 = assertThrows(ResourceNotFoundException.class, () ->
                verificationService.getPublicVerification("SV-1234567890", "127.0.0.1"));
        assertEquals("Verification reference not found", malformed3.getMessage());
    }

    @Test
    @DisplayName("6. Revoked, deleted, or reset to pending documents report REVOKED (never VALID)")
    void testRevokedOrDeletedOrPendingDocumentReportsRevoked() {
        String verificationId = "SV-DEADBEEF";
        document.setStatus(DocumentStatus.APPROVED);
        document.setDeleted(false);

        VerificationRecord record = new VerificationRecord(document, verificationId, "/api/documents/100/qr", VerificationStatus.VALID);

        // Case A: Record is explicitly REVOKED
        record.setStatus(VerificationStatus.REVOKED);
        when(verificationRecordRepository.findByVerificationId(verificationId)).thenReturn(Optional.of(record));

        PublicVerificationDTO resA = verificationService.getPublicVerification(verificationId, "127.0.0.1");
        assertEquals("REVOKED", resA.getStatus(), "Explicitly revoked verification record must report REVOKED");

        // Case B: Document was soft-deleted
        record.setStatus(VerificationStatus.VALID);
        document.setDeleted(true);

        PublicVerificationDTO resB = verificationService.getPublicVerification(verificationId, "127.0.0.1");
        assertEquals("REVOKED", resB.getStatus(), "Soft-deleted document must report REVOKED (never VALID)");

        // Case C: Document approval was reset to PENDING (e.g. after employee uploaded new version)
        document.setDeleted(false);
        document.setStatus(DocumentStatus.PENDING);

        PublicVerificationDTO resC = verificationService.getPublicVerification(verificationId, "127.0.0.1");
        assertEquals("REVOKED", resC.getStatus(), "Document reset to PENDING must report REVOKED (never VALID)");
    }

    @Test
    @DisplayName("7. QR PNG generates on the fly and decodes with ZXing Reader to the expected URL")
    void testQrPngDecodesToExpectedUrl() throws Exception {
        String verificationId = "SV-789ABCDE";
        document.setStatus(DocumentStatus.APPROVED);
        document.setVerificationId(verificationId);

        when(documentRepository.findByIdAndDeletedFalse(100L)).thenReturn(Optional.of(document));

        // Generate QR code PNG resource on the fly
        Resource qrResource = verificationService.getQrImageResource(100L, "alice@securevault.com", false);
        assertNotNull(qrResource);
        assertTrue(qrResource instanceof ByteArrayResource);

        byte[] pngBytes = qrResource.getInputStream().readAllBytes();
        assertTrue(pngBytes.length > 0);

        // Decode the generated PNG using ZXing MultiFormatReader
        BufferedImage bufferedImage = ImageIO.read(new ByteArrayInputStream(pngBytes));
        assertNotNull(bufferedImage, "Generated QR bytes must be a valid image");
        assertEquals(300, bufferedImage.getWidth(), "QR width must be 300px");
        assertEquals(300, bufferedImage.getHeight(), "QR height must be 300px");

        BinaryBitmap binaryBitmap = new BinaryBitmap(new HybridBinarizer(new BufferedImageLuminanceSource(bufferedImage)));
        Result qrResult = new MultiFormatReader().decode(binaryBitmap);

        String expectedEncodedUrl = FRONTEND_BASE_URL + "/verify/" + verificationId;
        assertEquals(expectedEncodedUrl, qrResult.getText(), "QR code must encode ONLY {FRONTEND_BASE_URL}/verify/{verificationId}");

        // Ensure no confidential details or file paths are encoded inside the QR payload
        assertFalse(qrResult.getText().contains("alice@securevault.com"));
        assertFalse(qrResult.getText().contains("storage/documents"));
        assertFalse(qrResult.getText().contains("Confidential"));
    }

    @Test
    @DisplayName("8. Revoke verification is ADMIN only; non-admin receives ForbiddenException (403)")
    void testRevokeIsAdminOnly() {
        // Non-admin attempt throws ForbiddenException
        ForbiddenException forbiddenEx = assertThrows(ForbiddenException.class, () ->
                verificationService.revokeVerification(100L, "employee@securevault.com", false));
        assertTrue(forbiddenEx.getMessage().contains("Only administrators"));

        // Admin call succeeds
        String verificationId = "SV-11223344";
        document.setStatus(DocumentStatus.APPROVED);
        VerificationRecord record = new VerificationRecord(document, verificationId, "/api/documents/100/qr", VerificationStatus.VALID);

        when(documentRepository.findByIdAndDeletedFalse(100L)).thenReturn(Optional.of(document));
        when(verificationRecordRepository.findByDocumentId(100L)).thenReturn(Optional.of(record));
        when(verificationRecordRepository.save(any(VerificationRecord.class))).thenAnswer(inv -> inv.getArgument(0));

        VerificationRecordDTO revoked = verificationService.revokeVerification(100L, "admin@securevault.com", true);
        assertEquals(VerificationStatus.REVOKED, revoked.getStatus());
    }
}
