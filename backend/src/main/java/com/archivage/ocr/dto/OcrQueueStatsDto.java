package com.archivage.ocr.dto;

public record OcrQueueStatsDto(
        long pending,
        long processing,
        long failed,
        long cancelled
) {
}
