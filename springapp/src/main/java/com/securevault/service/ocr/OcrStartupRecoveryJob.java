package com.securevault.service.ocr;

import com.securevault.entity.DocumentVersion;
import com.securevault.entity.OcrStatus;
import com.securevault.repository.DocumentVersionRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class OcrStartupRecoveryJob {

    private static final Logger logger = LoggerFactory.getLogger(OcrStartupRecoveryJob.class);

    private final DocumentVersionRepository documentVersionRepository;
    private final OcrProcessingService ocrProcessingService;

    public OcrStartupRecoveryJob(DocumentVersionRepository documentVersionRepository,
                                 OcrProcessingService ocrProcessingService) {
        this.documentVersionRepository = documentVersionRepository;
        this.ocrProcessingService = ocrProcessingService;
    }

    /**
     * One-time startup job that identifies any document versions stuck in PENDING status
     * for more than 2 minutes and re-queues them for asynchronous OCR processing.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void recoverStuckPendingVersions() {
        try {
            LocalDateTime cutoff = LocalDateTime.now().minusMinutes(2);
            List<DocumentVersion> stuckVersions = documentVersionRepository.findByOcrStatusAndCreatedAtBefore(
                    OcrStatus.PENDING, cutoff);

            if (stuckVersions == null || stuckVersions.isEmpty()) {
                logger.info("OCR Startup Recovery: No versions stuck in PENDING status for > 2 minutes.");
                return;
            }

            logger.info("OCR Startup Recovery: Found {} version(s) stuck in PENDING status for > 2 minutes. Re-queuing...",
                    stuckVersions.size());

            for (DocumentVersion version : stuckVersions) {
                logger.info("Re-queuing stuck OCR for document version ID: {} (created at {})",
                        version.getId(), version.getCreatedAt());
                ocrProcessingService.processOcrAsync(version.getId());
            }
        } catch (Throwable t) {
            logger.error("Error during OCR startup recovery job: {}", t.getMessage(), t);
        }
    }
}
