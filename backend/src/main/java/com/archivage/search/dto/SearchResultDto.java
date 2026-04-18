package com.archivage.search.dto;

import com.archivage.common.domain.DocumentStatus;

import java.time.LocalDate;
import java.util.UUID;

public record SearchResultDto(
        Long id,
        UUID uuid,
        String title,
        String documentTypeCode,
        String folderNumber,
        LocalDate documentDate,
        DocumentStatus status,
        String highlightTitle,
        String highlightContent,
        Double score
) {
}
