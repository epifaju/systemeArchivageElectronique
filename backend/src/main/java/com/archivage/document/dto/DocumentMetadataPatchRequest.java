package com.archivage.document.dto;

import com.archivage.common.domain.ConfidentialityLevel;
import com.archivage.common.domain.DocumentLanguage;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Mise à jour partielle : uniquement les champs non {@code null} remplacent les valeurs existantes.
 * Les listes de tags : {@code null} = pas de changement ; liste vide = retirer toutes les étiquettes.
 * Les métadonnées métier : {@code null} = pas de changement ; {@code Map} vide = réinitialiser (soumis à validation du type).
 */
public record DocumentMetadataPatchRequest(
        @Size(max = 500) String title,
        Long documentTypeId,
        @Size(max = 100) String folderNumber,
        LocalDate documentDate,
        DocumentLanguage language,
        ConfidentialityLevel confidentialityLevel,
        Long departmentId,
        @Size(max = 100) String externalReference,
        @Size(max = 200) String author,
        String notes,
        List<@Size(max = 100) String> tags,
        Map<String, Object> customMetadata
) {
}
