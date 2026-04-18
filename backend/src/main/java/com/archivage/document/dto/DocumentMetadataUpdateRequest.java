package com.archivage.document.dto;

import com.archivage.common.domain.ConfidentialityLevel;
import com.archivage.common.domain.DocumentLanguage;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public record DocumentMetadataUpdateRequest(
        @NotBlank @Size(max = 500) String title,
        @NotNull Long documentTypeId,
        @NotBlank @Size(max = 100) String folderNumber,
        @NotNull LocalDate documentDate,
        @NotNull DocumentLanguage language,
        @NotNull ConfidentialityLevel confidentialityLevel,
        Long departmentId,
        @Size(max = 100) String externalReference,
        @Size(max = 200) String author,
        String notes,
        List<@Size(max = 100) String> tags,
        /**
         * Si présent (y compris objet vide), remplace les champs métier.
         * Si absent ou null : inchangé ; si le type documentaire change, les champs métier sont réinitialisés.
         */
        Map<String, Object> customMetadata
) {
}
