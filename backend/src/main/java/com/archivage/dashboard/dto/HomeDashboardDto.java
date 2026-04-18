package com.archivage.dashboard.dto;

import com.archivage.ocr.dto.OcrQueueStatsDto;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record HomeDashboardDto(
        String welcomeName,
        long totalDocuments,
        Map<String, Long> documentsByStatus,
        long documentsLast7Days,
        OcrQueueStatsDto ocrQueue
) {
}
