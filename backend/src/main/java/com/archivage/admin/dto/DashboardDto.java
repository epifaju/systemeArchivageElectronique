package com.archivage.admin.dto;

import com.archivage.common.domain.DocumentStatus;
import com.archivage.ocr.dto.OcrQueueStatsDto;

import java.util.Map;

public record DashboardDto(
        long totalDocuments,
        Map<DocumentStatus, Long> documentsByStatus,
        OcrQueueStatsDto ocrQueue
) {
}
