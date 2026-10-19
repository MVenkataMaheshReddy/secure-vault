package com.securevault.service.ocr;

import com.securevault.entity.DocumentVersion;
import com.securevault.entity.OcrStatus;
import com.securevault.repository.DocumentVersionRepository;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OcrProcessingService {

    private static final Logger logger = LoggerFactory.getLogger(OcrProcessingService.class);

    private final DocumentVersionRepository documentVersionRepository;
    private final CompositeTextExtractionService compositeTextExtractionService;

    public OcrProcessingService(DocumentVersionRepository documentVersionRepository,
                                CompositeTextExtractionService compositeTextExtractionService) {
        this.documentVersionRepository = documentVersionRepository;
        this.compositeTextExtractionService = compositeTextExtractionService;
    }

    /**
     * Executes OCR processing asynchronously on the bounded ocrTaskExecutor thread pool.
     * Lives in a separate bean from its caller (DocumentService) to ensure Spring async proxy execution.
     */
    @Async("ocrTaskExecutor")
    public void processOcrAsync(Long versionId) {
        logger.info("Starting async OCR processing for document version: {}", versionId);
        processOcr(versionId);
    }

    /**
     * Synchronous OCR processing logic. Can be invoked directly or via processOcrAsync.
     */
    @Transactional
    public void processOcr(Long versionId) {
        Optional<DocumentVersion> versionOpt = Optional.empty();
        for (int attempt = 0; attempt < 5; attempt++) {
            versionOpt = documentVersionRepository.findById(versionId);
            if (versionOpt.isPresent()) {
                break;
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        if (versionOpt.isEmpty()) {
            logger.error("Document version with id {} not found for OCR processing after retries.", versionId);
            return;
        }

        DocumentVersion version = versionOpt.get();
        String filePathStr = version.getFilePath();
        if (filePathStr == null || filePathStr.isBlank()) {
            version.setOcrStatus(OcrStatus.FAILED);
            version.setOcrErrorMessage("File path is missing or blank");
            documentVersionRepository.save(version);
            return;
        }

        Path filePath = Paths.get(filePathStr);
        if (!Files.exists(filePath)) {
            // Check alternate relative paths
            Path alt1 = Paths.get("..", filePathStr);
            Path alt2 = Paths.get("springapp", filePathStr);
            if (Files.exists(alt1)) {
                filePath = alt1;
            } else if (Files.exists(alt2)) {
                filePath = alt2;
            } else {
                logger.error("File '{}' not found on disk for document version {}. Marking OCR_FAILED.", filePathStr, versionId);
                version.setOcrStatus(OcrStatus.FAILED);
                version.setOcrErrorMessage("File not found on disk: " + filePathStr);
                documentVersionRepository.save(version);
                return;
            }
        }

        String contentType = version.getContentType();
        String fileName = filePath.getFileName().toString();

        if (!compositeTextExtractionService.isSupported(contentType, fileName)) {
            logger.info("Content type '{}' / file '{}' is not supported for OCR. Marking SKIPPED.", contentType, fileName);
            version.setOcrStatus(OcrStatus.SKIPPED);
            version.setOcrErrorMessage(null);
            documentVersionRepository.save(version);
            return;
        }

        try {
            String extractedText = compositeTextExtractionService.extractText(filePath, contentType, fileName);
            version.setOcrText(extractedText);
            version.setOcrStatus(OcrStatus.DONE);
            version.setOcrErrorMessage(null);
            logger.info("OCR successfully completed for version {}. Extracted {} characters.",
                    versionId, extractedText != null ? extractedText.length() : 0);
        } catch (Throwable t) {
            logger.error("OCR extraction failed for version {}: {}", versionId, t.getMessage(), t);
            version.setOcrStatus(OcrStatus.FAILED);
            String errMsg = t.getMessage();
            if (errMsg == null || errMsg.isBlank()) {
                errMsg = t.getClass().getSimpleName();
            }
            if (errMsg.length() > 500) {
                errMsg = errMsg.substring(0, 500);
            }
            version.setOcrErrorMessage(errMsg);
        }

        documentVersionRepository.save(version);
    }
}
