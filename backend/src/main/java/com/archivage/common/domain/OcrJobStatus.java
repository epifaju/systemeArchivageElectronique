package com.archivage.common.domain;

public enum OcrJobStatus {
    PENDING,
    PROCESSING,
    OCR_SUCCESS,
    OCR_PARTIAL,
    OCR_FAILED,
    NEEDS_REVIEW,
    /** Annulé avant traitement (file uniquement). */
    CANCELLED
}
