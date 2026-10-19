package com.securevault.service;

import com.securevault.exception.BadRequestException;
import com.securevault.exception.ResourceNotFoundException;
import com.securevault.util.FileValidationUtil;
import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;
import javax.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class FileStorageService {

    private final Path fileStorageLocation;
    private final FileValidationUtil fileValidationUtil;

    public FileStorageService(
            @Value("${securevault.storage.upload-dir:storage/documents}") String uploadDir,
            FileValidationUtil fileValidationUtil) {
        this.fileStorageLocation = Paths.get(uploadDir).toAbsolutePath().normalize();
        this.fileValidationUtil = fileValidationUtil;
    }

    @PostConstruct
    public void init() {
        try {
            Files.createDirectories(this.fileStorageLocation);
        } catch (IOException ex) {
            throw new IllegalStateException("Could not initialize storage directory at " + this.fileStorageLocation, ex);
        }
    }

    public StoredFileResult storeFile(MultipartFile file, Long documentId, Integer versionNumber) {
        // Run comprehensive validation (magic bytes, whitelist, size, path traversal)
        FileValidationUtil.ValidatedFileResult validationResult = fileValidationUtil.validateFile(file);

        try {
            // Organize under documentId/versionNumber directory
            Path versionDir = this.fileStorageLocation
                    .resolve(String.valueOf(documentId != null ? documentId : 0))
                    .resolve(String.valueOf(versionNumber != null ? versionNumber : 1))
                    .normalize();

            // Block directory traversal outside base upload path
            if (!versionDir.startsWith(this.fileStorageLocation)) {
                throw new BadRequestException("Invalid path or directory traversal detected");
            }

            Files.createDirectories(versionDir);

            // Store strictly with a generated UUID filename (never the user's uploaded name)
            String uuidFilename = UUID.randomUUID().toString() + "." + validationResult.getExtension();
            Path targetLocation = versionDir.resolve(uuidFilename).normalize();

            if (!targetLocation.startsWith(this.fileStorageLocation)) {
                throw new BadRequestException("Path traversal attempt detected in target file location");
            }

            Files.copy(file.getInputStream(), targetLocation, StandardCopyOption.REPLACE_EXISTING);

            return new StoredFileResult(
                    targetLocation.toString(),
                    uuidFilename,
                    validationResult.getFileSize(),
                    validationResult.getDetectedContentType(),
                    validationResult.getChecksum()
            );
        } catch (IOException ex) {
            throw new IllegalStateException("Could not store file physically: " + validationResult.getSanitizedOriginalName(), ex);
        }
    }

    public Resource loadFileAsResource(String filePath) {
        try {
            Path file = Paths.get(filePath).toAbsolutePath().normalize();

            // Block path traversal outside base upload path
            if (!file.startsWith(this.fileStorageLocation)) {
                throw new BadRequestException("Path traversal attempt detected in file location: " + filePath);
            }

            // Verify file exists
            if (!Files.exists(file)) {
                throw new ResourceNotFoundException("File not found on storage: " + filePath);
            }

            Resource resource = new UrlResource(file.toUri());
            if (resource.exists() && resource.isReadable()) {
                return resource;
            } else {
                throw new ResourceNotFoundException("File not found or not readable: " + filePath);
            }
        } catch (MalformedURLException ex) {
            throw new ResourceNotFoundException("File path is invalid: " + filePath);
        }
    }

    public byte[] loadFileAsBytes(String filePath) {
        try {
            Path file = Paths.get(filePath).toAbsolutePath().normalize();

            // Block path traversal outside base upload path
            if (!file.startsWith(this.fileStorageLocation)) {
                throw new BadRequestException("Path traversal attempt detected in file location: " + filePath);
            }

            if (!Files.exists(file)) {
                throw new ResourceNotFoundException("File not found on disk: " + filePath);
            }
            return Files.readAllBytes(file);
        } catch (IOException ex) {
            throw new ResourceNotFoundException("Could not read file from disk: " + filePath);
        }
    }

    public StoredFileResult copyVersionFile(String sourceFilePath, Long documentId, Integer newVersionNumber, String fallbackContentType) {
        try {
            Path sourceFile = Paths.get(sourceFilePath).toAbsolutePath().normalize();

            // Block path traversal outside base upload path
            if (!sourceFile.startsWith(this.fileStorageLocation)) {
                throw new BadRequestException("Path traversal attempt detected in source file location: " + sourceFilePath);
            }

            if (!Files.exists(sourceFile)) {
                throw new ResourceNotFoundException("Source file not found for restore: " + sourceFilePath);
            }

            // Organize under documentId/newVersionNumber directory
            Path targetVersionDir = this.fileStorageLocation
                    .resolve(String.valueOf(documentId != null ? documentId : 0))
                    .resolve(String.valueOf(newVersionNumber != null ? newVersionNumber : 1))
                    .normalize();

            if (!targetVersionDir.startsWith(this.fileStorageLocation)) {
                throw new BadRequestException("Invalid path or directory traversal detected");
            }

            Files.createDirectories(targetVersionDir);

            String extension = FileValidationUtil.getExtension(sourceFile.getFileName().toString());
            String uuidFilename = UUID.randomUUID().toString() + (extension.isEmpty() ? "" : "." + extension);
            Path targetFile = targetVersionDir.resolve(uuidFilename).normalize();

            if (!targetFile.startsWith(this.fileStorageLocation)) {
                throw new BadRequestException("Path traversal attempt detected in target file location");
            }

            Files.copy(sourceFile, targetFile, StandardCopyOption.REPLACE_EXISTING);

            byte[] bytes = Files.readAllBytes(targetFile);
            long fileSize = bytes.length;
            String checksum = FileValidationUtil.computeSha256(bytes);
            String contentType = fallbackContentType;
            if (contentType == null || contentType.isBlank()) {
                contentType = Files.probeContentType(targetFile);
            }
            if (contentType == null) {
                contentType = "application/octet-stream";
            }

            return new StoredFileResult(
                    targetFile.toString(),
                    uuidFilename,
                    fileSize,
                    contentType,
                    checksum
            );
        } catch (IOException ex) {
            throw new IllegalStateException("Could not copy version file for restore: " + sourceFilePath, ex);
        }
    }

    public static class StoredFileResult {
        private final String filePath;
        private final String storedFileName;
        private final Long fileSize;
        private final String contentType;
        private final String checksum;

        public StoredFileResult(String filePath, String storedFileName, Long fileSize, String contentType, String checksum) {
            this.filePath = filePath;
            this.storedFileName = storedFileName;
            this.fileSize = fileSize;
            this.contentType = contentType;
            this.checksum = checksum;
        }

        public String getFilePath() {
            return filePath;
        }

        public String getStoredFileName() {
            return storedFileName;
        }

        public Long getFileSize() {
            return fileSize;
        }

        public String getContentType() {
            return contentType;
        }

        public String getChecksum() {
            return checksum;
        }
    }
}
