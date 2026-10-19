package com.securevault.util;

import com.securevault.exception.BadRequestException;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.tika.Tika;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Component
public class FileValidationUtil {

    private final Tika tika = new Tika();

    private final long maxDocSizeBytes;
    private final long maxImageSizeBytes;

    public static final Set<String> ALLOWED_EXTENSIONS = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList("pdf", "docx", "png", "jpg", "jpeg"))
    );

    private static final Map<String, List<String>> EXTENSION_MIME_MAP = new HashMap<>();

    static {
        EXTENSION_MIME_MAP.put("pdf", Collections.singletonList("application/pdf"));
        EXTENSION_MIME_MAP.put("docx", Arrays.asList(
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "application/x-tika-ooxml",
                "application/zip"
        ));
        EXTENSION_MIME_MAP.put("png", Collections.singletonList("image/png"));
        EXTENSION_MIME_MAP.put("jpg", Collections.singletonList("image/jpeg"));
        EXTENSION_MIME_MAP.put("jpeg", Collections.singletonList("image/jpeg"));
    }

    public FileValidationUtil(
            @Value("${securevault.upload.max-doc-size:10485760}") long maxDocSizeBytes,
            @Value("${securevault.upload.max-image-size:5242880}") long maxImageSizeBytes) {
        this.maxDocSizeBytes = maxDocSizeBytes;
        this.maxImageSizeBytes = maxImageSizeBytes;
    }

    public FileValidationUtil() {
        this(10 * 1024 * 1024L, 5 * 1024 * 1024L); // 10MB doc, 5MB image default
    }

    public static class ValidatedFileResult {
        private final String sanitizedOriginalName;
        private final String extension;
        private final String detectedContentType;
        private final long fileSize;
        private final String checksum;

        public ValidatedFileResult(String sanitizedOriginalName, String extension,
                                   String detectedContentType, long fileSize, String checksum) {
            this.sanitizedOriginalName = sanitizedOriginalName;
            this.extension = extension;
            this.detectedContentType = detectedContentType;
            this.fileSize = fileSize;
            this.checksum = checksum;
        }

        public String getSanitizedOriginalName() {
            return sanitizedOriginalName;
        }

        public String getExtension() {
            return extension;
        }

        public String getDetectedContentType() {
            return detectedContentType;
        }

        public long getFileSize() {
            return fileSize;
        }

        public String getChecksum() {
            return checksum;
        }
    }

    /**
     * Validates a multipart file comprehensively against:
     * 1. Empty / null check
     * 2. Path traversal attack patterns in filename
     * 3. Extension whitelist (pdf, docx, png, jpg, jpeg)
     * 4. File size limits by type
     * 5. Real magic-byte MIME type inspection via Apache Tika
     * 6. Computes SHA-256 checksum
     */
    public ValidatedFileResult validateFile(MultipartFile file) {
        if (file == null || file.isEmpty() || file.getSize() == 0) {
            throw new BadRequestException("File is empty or not provided");
        }

        String rawFilename = file.getOriginalFilename();
        if (rawFilename == null || rawFilename.trim().isEmpty()) {
            throw new BadRequestException("File name cannot be empty");
        }

        // 1. Path traversal detection
        if (rawFilename.contains("..") || rawFilename.contains("/") || rawFilename.contains("\\")) {
            throw new BadRequestException("Path traversal or illegal path characters detected in file name: " + rawFilename);
        }

        // 2. Sanitize filename
        String sanitizedFilename = sanitizeFilename(rawFilename);

        // 3. Extract and validate extension
        String extension = getExtension(sanitizedFilename);
        if (extension.isEmpty() || !ALLOWED_EXTENSIONS.contains(extension)) {
            throw new BadRequestException("File type not allowed. Allowed types: " + String.join(", ", ALLOWED_EXTENSIONS));
        }

        // 4. File size validation
        long fileSize = file.getSize();
        if ("pdf".equals(extension) || "docx".equals(extension)) {
            if (fileSize > maxDocSizeBytes) {
                throw new BadRequestException("Document file size (" + fileSize + " bytes) exceeds maximum limit of " + (maxDocSizeBytes / (1024 * 1024)) + " MB");
            }
        } else {
            // Images (png, jpg, jpeg)
            if (fileSize > maxImageSizeBytes) {
                throw new BadRequestException("Image file size (" + fileSize + " bytes) exceeds maximum limit of " + (maxImageSizeBytes / (1024 * 1024)) + " MB");
            }
        }

        // 5. Inspect real magic bytes using Apache Tika
        String detectedMimeType;
        try (InputStream is = new BufferedInputStream(file.getInputStream())) {
            detectedMimeType = tika.detect(is, sanitizedFilename);
        } catch (IOException e) {
            throw new BadRequestException("Failed to inspect file content: " + e.getMessage());
        }

        List<String> allowedMimesForExt = EXTENSION_MIME_MAP.get(extension);
        if (allowedMimesForExt == null || !allowedMimesForExt.contains(detectedMimeType)) {
            throw new BadRequestException("Spoofed or invalid file detected! File extension ." + extension
                    + " does not match detected content type: " + detectedMimeType);
        }

        // 6. Compute SHA-256 Checksum
        String checksum;
        try {
            checksum = computeSha256(file.getBytes());
        } catch (IOException e) {
            throw new BadRequestException("Failed to compute file checksum: " + e.getMessage());
        }

        return new ValidatedFileResult(sanitizedFilename, extension, detectedMimeType, fileSize, checksum);
    }

    public String sanitizeFilename(String filename) {
        if (filename == null) return "file";
        Path path = Paths.get(filename).getFileName();
        String name = path != null ? path.toString() : filename;

        // Strip path traversal prefixes
        name = name.replaceAll("[.]{2,}", "_");
        // Keep alphanumeric, dash, underscore, dot
        name = name.replaceAll("[^a-zA-Z0-9._-]", "_");

        if (name.isEmpty() || name.startsWith(".")) {
            name = "file_" + System.currentTimeMillis() + name;
        }

        return name;
    }

    public static String getExtension(String filename) {
        if (filename == null) return "";
        int dotIndex = filename.lastIndexOf('.');
        if (dotIndex >= 0 && dotIndex < filename.length() - 1) {
            return filename.substring(dotIndex + 1).toLowerCase();
        }
        return "";
    }

    public static String computeSha256(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data);
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }
}
