package com.securevault.service.ocr;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class DocxTextExtractionService implements TextExtractionService {

    private static final Logger logger = LoggerFactory.getLogger(DocxTextExtractionService.class);

    @Override
    public boolean supports(String contentType, String fileName) {
        if (contentType != null && (contentType.contains("wordprocessingml") || contentType.equalsIgnoreCase("application/msword"))) {
            return true;
        }
        return fileName != null && fileName.toLowerCase().endsWith(".docx");
    }

    @Override
    public String extractText(Path filePath) throws Exception {
        try (InputStream is = Files.newInputStream(filePath);
             XWPFDocument document = new XWPFDocument(is);
             XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {
            String text = extractor.getText();
            return text != null ? text.trim() : "";
        } catch (Exception e) {
            logger.warn("Failed to extract text from DOCX file {}: {}", filePath.getFileName(), e.getMessage());
            throw new OcrExtractionException("Failed to extract text from DOCX file: " + e.getMessage(), e);
        }
    }
}
