package com.securevault.service.ocr;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class CompositeTextExtractionService {

    private final List<TextExtractionService> extractors;

    public CompositeTextExtractionService(List<TextExtractionService> extractors) {
        this.extractors = extractors;
    }

    public boolean isSupported(String contentType, String fileName) {
        return extractors.stream().anyMatch(e -> e.supports(contentType, fileName));
    }

    public Optional<TextExtractionService> getExtractor(String contentType, String fileName) {
        return extractors.stream()
                .filter(e -> e.supports(contentType, fileName))
                .findFirst();
    }

    public String extractText(Path filePath, String contentType, String fileName) throws Exception {
        TextExtractionService extractor = getExtractor(contentType, fileName)
                .orElseThrow(() -> new UnsupportedOperationException("No text extractor registered for contentType: " + contentType + ", fileName: " + fileName));
        return extractor.extractText(filePath);
    }

    public List<TextExtractionService> getExtractors() {
        return extractors;
    }
}
