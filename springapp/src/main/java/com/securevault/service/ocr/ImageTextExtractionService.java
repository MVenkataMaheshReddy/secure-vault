package com.securevault.service.ocr;

import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class ImageTextExtractionService implements TextExtractionService {

    private static final Logger logger = LoggerFactory.getLogger(ImageTextExtractionService.class);
    private final TesseractOcrService tesseractOcrService;

    public ImageTextExtractionService(TesseractOcrService tesseractOcrService) {
        this.tesseractOcrService = tesseractOcrService;
    }

    @Override
    public boolean supports(String contentType, String fileName) {
        if (contentType != null && contentType.startsWith("image/")) {
            return true;
        }
        if (fileName != null) {
            String lower = fileName.toLowerCase();
            return lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg")
                    || lower.endsWith(".bmp") || lower.endsWith(".tiff") || lower.endsWith(".gif");
        }
        return false;
    }

    @Override
    public String extractText(Path filePath) throws Exception {
        String result = tesseractOcrService.doOcr(filePath.toFile());
        return result != null ? result.trim() : "";
    }
}
