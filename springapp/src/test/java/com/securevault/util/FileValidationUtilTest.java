package com.securevault.util;

import com.securevault.exception.BadRequestException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import static org.junit.jupiter.api.Assertions.*;

public class FileValidationUtilTest {

    private FileValidationUtil validationUtil;

    // Standard valid signatures
    private static final byte[] VALID_PDF_BYTES =
            "%PDF-1.5\n%\n1 0 obj\n<<>>\nendobj\ntrailer\n<<>>\n%%EOF".getBytes(StandardCharsets.US_ASCII);

    private static final byte[] VALID_PNG_BYTES = new byte[]{
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
            0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52,
            0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01,
            0x08, 0x06, 0x00, 0x00, 0x00, 0x1F, 0x15, (byte) 0xC4, (byte) 0x89
    };

    private static final byte[] VALID_JPEG_BYTES = new byte[]{
            (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0,
            0x00, 0x10, 0x4A, 0x46, 0x49, 0x46, 0x00, 0x01,
            0x01, 0x01, 0x00, 0x48, 0x00, 0x48, 0x00, 0x00,
            (byte) 0xFF, (byte) 0xD9
    };

    // DOS / Windows Executable (MZ header)
    private static final byte[] SPOOFED_EXE_BYTES = new byte[]{
            'M', 'Z', (byte) 0x90, 0x00, 0x03, 0x00, 0x00, 0x00,
            0x04, 0x00, 0x00, 0x00, (byte) 0xFF, (byte) 0xFF, 0x00, 0x00
    };

    private byte[] createMinimalDocxBytes() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            ZipEntry entry = new ZipEntry("[Content_Types].xml");
            zos.putNextEntry(entry);
            zos.write("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"></Types>".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }
        return baos.toByteArray();
    }

    @BeforeEach
    void setUp() {
        validationUtil = new FileValidationUtil(10 * 1024 * 1024L, 5 * 1024 * 1024L);
    }

    @Test
    void testValidPdf_PassesValidation() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "report.pdf", "application/pdf", VALID_PDF_BYTES
        );

        FileValidationUtil.ValidatedFileResult result = validationUtil.validateFile(file);

        assertNotNull(result);
        assertEquals("report.pdf", result.getSanitizedOriginalName());
        assertEquals("pdf", result.getExtension());
        assertEquals("application/pdf", result.getDetectedContentType());
        assertNotNull(result.getChecksum());
        assertFalse(result.getChecksum().isEmpty());
    }

    @Test
    void testValidPng_PassesValidation() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "avatar.png", "image/png", VALID_PNG_BYTES
        );

        FileValidationUtil.ValidatedFileResult result = validationUtil.validateFile(file);

        assertNotNull(result);
        assertEquals("avatar.png", result.getSanitizedOriginalName());
        assertEquals("png", result.getExtension());
        assertEquals("image/png", result.getDetectedContentType());
    }

    @Test
    void testValidJpeg_PassesValidation() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "photo.jpg", "image/jpeg", VALID_JPEG_BYTES
        );

        FileValidationUtil.ValidatedFileResult result = validationUtil.validateFile(file);

        assertNotNull(result);
        assertEquals("photo.jpg", result.getSanitizedOriginalName());
        assertEquals("jpg", result.getExtension());
        assertEquals("image/jpeg", result.getDetectedContentType());
    }

    @Test
    void testValidDocx_PassesValidation() throws IOException {
        byte[] docxBytes = createMinimalDocxBytes();
        MockMultipartFile file = new MockMultipartFile(
                "file", "document.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                docxBytes
        );

        FileValidationUtil.ValidatedFileResult result = validationUtil.validateFile(file);

        assertNotNull(result);
        assertEquals("document.docx", result.getSanitizedOriginalName());
        assertEquals("docx", result.getExtension());
    }

    @Test
    void testSpoofedExtension_ExeDisguisedAsPdf_IsRejected() {
        // Attack: Attacker renames malicious .exe to invoice.pdf
        MockMultipartFile file = new MockMultipartFile(
                "file", "invoice.pdf", "application/pdf", SPOOFED_EXE_BYTES
        );

        BadRequestException ex = assertThrows(BadRequestException.class, () ->
                validationUtil.validateFile(file)
        );

        assertTrue(ex.getMessage().contains("Spoofed or invalid file detected"));
    }

    @Test
    void testSpoofedExtension_PlainTextDisguisedAsPng_IsRejected() {
        byte[] plainTextBytes = "Hello this is just a plain text file pretending to be PNG".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile file = new MockMultipartFile(
                "file", "secret.png", "image/png", plainTextBytes
        );

        BadRequestException ex = assertThrows(BadRequestException.class, () ->
                validationUtil.validateFile(file)
        );

        assertTrue(ex.getMessage().contains("Spoofed or invalid file detected"));
    }

    @Test
    void testDisallowedExtension_IsRejected() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "script.sh", "application/x-sh", "#!/bin/bash\necho hi".getBytes(StandardCharsets.UTF_8)
        );

        BadRequestException ex = assertThrows(BadRequestException.class, () ->
                validationUtil.validateFile(file)
        );

        assertTrue(ex.getMessage().contains("File type not allowed"));
    }

    @Test
    void testEmptyFile_IsRejected() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "empty.pdf", "application/pdf", new byte[0]
        );

        BadRequestException ex = assertThrows(BadRequestException.class, () ->
                validationUtil.validateFile(file)
        );

        assertTrue(ex.getMessage().contains("File is empty or not provided"));
    }

    @Test
    void testPathTraversal_RelativeDots_IsRejected() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "../../etc/passwd.pdf", "application/pdf", VALID_PDF_BYTES
        );

        BadRequestException ex = assertThrows(BadRequestException.class, () ->
                validationUtil.validateFile(file)
        );

        assertTrue(ex.getMessage().contains("Path traversal or illegal path characters detected"));
    }

    @Test
    void testPathTraversal_ForwardSlash_IsRejected() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "subfolder/document.pdf", "application/pdf", VALID_PDF_BYTES
        );

        BadRequestException ex = assertThrows(BadRequestException.class, () ->
                validationUtil.validateFile(file)
        );

        assertTrue(ex.getMessage().contains("Path traversal or illegal path characters detected"));
    }

    @Test
    void testPathTraversal_Backslash_IsRejected() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "..\\system32\\evil.pdf", "application/pdf", VALID_PDF_BYTES
        );

        BadRequestException ex = assertThrows(BadRequestException.class, () ->
                validationUtil.validateFile(file)
        );

        assertTrue(ex.getMessage().contains("Path traversal or illegal path characters detected"));
    }

    @Test
    void testOversizedDocument_ExceedsLimit_IsRejected() {
        // Custom limit: 20 bytes for docs (VALID_PDF_BYTES is 49 bytes)
        FileValidationUtil strictUtil = new FileValidationUtil(20L, 10L);

        MockMultipartFile file = new MockMultipartFile(
                "file", "large.pdf", "application/pdf", VALID_PDF_BYTES // length > 50
        );

        BadRequestException ex = assertThrows(BadRequestException.class, () ->
                strictUtil.validateFile(file)
        );

        assertTrue(ex.getMessage().contains("exceeds maximum limit"));
    }

    @Test
    void testOversizedImage_ExceedsLimit_IsRejected() {
        // Custom limit: 10 bytes for images
        FileValidationUtil strictUtil = new FileValidationUtil(1024L, 10L);

        MockMultipartFile file = new MockMultipartFile(
                "file", "large.png", "image/png", VALID_PNG_BYTES // length > 10
        );

        BadRequestException ex = assertThrows(BadRequestException.class, () ->
                strictUtil.validateFile(file)
        );

        assertTrue(ex.getMessage().contains("Image file size"));
    }

    @Test
    void testSanitizeFilename() {
        assertEquals("report_123.pdf", validationUtil.sanitizeFilename("report 123.pdf"));
        assertEquals("normal-file.docx", validationUtil.sanitizeFilename("normal-file.docx"));
        assertFalse(validationUtil.sanitizeFilename("../evil.pdf").contains(".."));
    }

    @Test
    void testChecksumComputation() {
        byte[] content = "Hello SecureVault".getBytes(StandardCharsets.UTF_8);
        String checksum = FileValidationUtil.computeSha256(content);
        assertNotNull(checksum);
        assertEquals(64, checksum.length()); // SHA-256 is 64 hex characters
    }
}
