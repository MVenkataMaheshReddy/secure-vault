package com.securevault.service.ocr;

import java.nio.file.Path;

public interface TextExtractionService {

    /**
     * Determines whether this extractor supports the given MIME content type or file name.
     *
     * @param contentType MIME type (e.g. application/pdf)
     * @param fileName original or stored file name
     * @return true if supported, false otherwise
     */
    boolean supports(String contentType, String fileName);

    /**
     * Extracts text from the physical file.
     *
     * @param filePath path to the file on disk
     * @return extracted plain text
     * @throws Exception if extraction fails
     */
    String extractText(Path filePath) throws Exception;
}
