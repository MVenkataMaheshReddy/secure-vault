package com.securevault.service.ocr;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class PdfTextExtractionService implements TextExtractionService {

    private static final Logger logger = LoggerFactory.getLogger(PdfTextExtractionService.class);
    private final TesseractOcrService tesseractOcrService;

    public PdfTextExtractionService(TesseractOcrService tesseractOcrService) {
        this.tesseractOcrService = tesseractOcrService;
    }

    @Override
    public boolean supports(String contentType, String fileName) {
        if (contentType != null && contentType.equalsIgnoreCase("application/pdf")) {
            return true;
        }
        return fileName != null && fileName.toLowerCase().endsWith(".pdf");
    }

    @Override
    public String extractText(Path filePath) throws Exception {
        try (PDDocument document = PDDocument.load(filePath.toFile())) {
            // 1. Try extracting embedded text first via PDFBox
            PDFTextStripper stripper = new PDFTextStripper();
            String embeddedText = stripper.getText(document);
            if (embeddedText != null && !embeddedText.trim().isEmpty()) {
                logger.debug("Successfully extracted embedded text from PDF: {}", filePath.getFileName());
                return embeddedText.trim();
            }

            // 2. Fall back to rendering pages into images and running Tess4J OCR
            logger.info("No embedded text found in PDF {}. Falling back to OCR page rendering.", filePath.getFileName());
            PDFRenderer renderer = new PDFRenderer(document);
            StringBuilder sb = new StringBuilder();
            int pages = Math.min(document.getNumberOfPages(), 10);
            for (int i = 0; i < pages; i++) {
                BufferedImage pageImage = renderer.renderImageWithDPI(i, 200, ImageType.RGB);
                String pageText = tesseractOcrService.doOcr(pageImage);
                if (pageText != null && !pageText.isBlank()) {
                    sb.append(pageText.trim()).append("\n");
                }
            }
            return sb.toString().trim();
        } catch (IOException e) {
            logger.warn("Failed to read PDF file {}: {}", filePath.getFileName(), e.getMessage());
            throw new OcrExtractionException("Failed to read PDF document: " + e.getMessage(), e);
        }
    }
}
