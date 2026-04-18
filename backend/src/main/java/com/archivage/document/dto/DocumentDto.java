package com.archivage.document.dto;

import com.archivage.common.domain.ConfidentialityLevel;
import com.archivage.common.domain.DocumentLanguage;
import com.archivage.common.domain.DocumentStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record DocumentDto(
        Long id,
        UUID uuid,
        Long documentTypeId,
        Long departmentId,
        String title,
        String documentTypeCode,
        String documentTypeLabelFr,
        String documentTypeLabelPt,
        String folderNumber,
        LocalDate documentDate,
        Instant archiveDate,
        DocumentStatus status,
        DocumentLanguage language,
        ConfidentialityLevel confidentialityLevel,
        String mimeType,
        boolean ocrAvailable,
        Long fileSize,
        Integer pageCount,
        String sha256,
        String externalReference,
        String author,
        String notes,
        List<String> tags,
        Instant createdAt,
        Instant updatedAt,
        /** Non null uniquement pour les documents supprimés logiquement (ex. liste admin). */
        Instant deletedAt
) {
}
