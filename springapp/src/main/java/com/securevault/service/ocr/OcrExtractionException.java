package com.securevault.service.ocr;

public class OcrExtractionException extends RuntimeException {

    public OcrExtractionException(String message) {
        super(message);
    }

    public OcrExtractionException(String message, Throwable cause) {
        super(message, cause);
    }
}
