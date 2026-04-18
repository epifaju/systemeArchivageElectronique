package com.archivage.ocr;

/**
 * Publié lorsqu'un job OCR est persisté. Le traitement asynchrone doit être déclenché
 * <strong>après commit</strong> pour que le worker voie les lignes en base (isolation READ COMMITTED).
 */
public record OcrJobCreatedEvent(long jobId) {
}
