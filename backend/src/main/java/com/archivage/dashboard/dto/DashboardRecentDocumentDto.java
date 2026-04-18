package com.archivage.dashboard.dto;

import com.archivage.common.domain.DocumentStatus;

import java.time.LocalDate;

public record DashboardRecentDocumentDto(
        Long id,
        String title,
        String documentTypeCode,
        String documentTypeLabelFr,
        String documentTypeLabelPt,
        LocalDate documentDate,
        DocumentStatus status
) {
}
