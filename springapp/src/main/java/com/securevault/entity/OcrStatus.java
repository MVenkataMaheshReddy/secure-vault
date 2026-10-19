package com.securevault.entity;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum OcrStatus {
    PENDING,
    DONE,
    FAILED,
    SKIPPED;

    public static final OcrStatus OCR_FAILED = FAILED;

    @JsonCreator
    public static OcrStatus fromString(String value) {
        if (value == null) {
            return PENDING;
        }
        if ("OCR_FAILED".equalsIgnoreCase(value)) {
            return FAILED;
        }
        try {
            return OcrStatus.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            return PENDING;
        }
    }
}
