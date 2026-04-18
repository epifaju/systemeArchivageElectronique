package com.archivage.common.domain;

public enum DocumentStatus {
    PENDING,
    PROCESSING,
    OCR_SUCCESS,
    OCR_PARTIAL,
    OCR_FAILED,
    NEEDS_REVIEW,
    VALIDATED,
    ARCHIVED
}
