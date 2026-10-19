package com.securevault.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;

@Service
public class QrCodeService {

    private static final Logger logger = LoggerFactory.getLogger(QrCodeService.class);

    private final String frontendBaseUrl;
    private final String qrStorageDir;
    private final SecureRandom secureRandom = new SecureRandom();

    public static final int QR_WIDTH = 300;
    public static final int QR_HEIGHT = 300;

    public QrCodeService(
            @Value("${securevault.frontend.base-url:http://localhost:3000}") String frontendBaseUrl,
            @Value("${securevault.qr.storage-dir:storage/qr}") String qrStorageDir) {
        this.frontendBaseUrl = frontendBaseUrl.replaceAll("/+$", "");
        this.qrStorageDir = qrStorageDir;
    }

    @PostConstruct
    public void init() {
        try {
            Files.createDirectories(Paths.get(qrStorageDir));
        } catch (IOException e) {
            logger.error("Failed to create QR code storage directory: {}", qrStorageDir, e);
        }
    }

    /**
     * Generates a unique verification ID in format "SV-" + 8 uppercase hex chars.
     * Example: "SV-3F8A0B12"
     */
    public String generateVerificationId() {
        byte[] randomBytes = new byte[4];
        secureRandom.nextBytes(randomBytes);
        StringBuilder sb = new StringBuilder("SV-");
        for (byte b : randomBytes) {
            sb.append(String.format("%02X", b));
        }
        return sb.toString();
    }

    /**
     * Builds the public verification URL.
     * Format: {FRONTEND_BASE_URL}/verify/{verificationId}
     */
    public String buildVerificationUrl(String verificationId) {
        return frontendBaseUrl + "/verify/" + verificationId;
    }

    /**
     * Generates a 300x300 PNG QR code containing ONLY the verification URL.
     * Saves the image to disk and returns the relative or absolute file path.
     */
    public String generateAndSaveQrCode(String verificationId) throws IOException {
        String verificationUrl = buildVerificationUrl(verificationId);
        byte[] pngData = generateQrImageBytes(verificationUrl, QR_WIDTH, QR_HEIGHT);

        Path storagePath = Paths.get(qrStorageDir);
        Files.createDirectories(storagePath);

        Path filePath = storagePath.resolve(verificationId + ".png");
        Files.write(filePath, pngData);
        logger.info("Generated and saved QR code for {} at {}", verificationId, filePath);

        return filePath.toString();
    }

    /**
     * Generates QR code PNG image as byte array.
     */
    public byte[] generateQrImageBytes(String text, int width, int height) {
        try {
            QRCodeWriter qrCodeWriter = new QRCodeWriter();
            Map<EncodeHintType, Object> hints = new HashMap<>();
            hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
            hints.put(EncodeHintType.MARGIN, 1);

            BitMatrix bitMatrix = qrCodeWriter.encode(text, BarcodeFormat.QR_CODE, width, height, hints);

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(bitMatrix, "PNG", outputStream);
            return outputStream.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate QR code: " + e.getMessage(), e);
        }
    }

    /**
     * Loads the saved QR code as a Spring Resource. If not on disk, regenerates it.
     */
    public Resource loadQrCodeAsResource(String verificationId) throws IOException {
        if (verificationId == null || !verificationId.matches("^SV-[0-9A-Fa-f]{8}$")) {
            throw new com.securevault.exception.BadRequestException("Invalid verification ID format: " + verificationId);
        }

        Path baseDir = Paths.get(qrStorageDir).toAbsolutePath().normalize();
        Path filePath = baseDir.resolve(verificationId + ".png").normalize();

        if (!filePath.startsWith(baseDir)) {
            throw new com.securevault.exception.BadRequestException("Path traversal detected in verification ID: " + verificationId);
        }

        if (!Files.exists(filePath)) {
            generateAndSaveQrCode(verificationId);
        }
        return new UrlResource(filePath.toUri());
    }

    public String getFrontendBaseUrl() {
        return frontendBaseUrl;
    }

    public String getQrStorageDir() {
        return qrStorageDir;
    }
}
