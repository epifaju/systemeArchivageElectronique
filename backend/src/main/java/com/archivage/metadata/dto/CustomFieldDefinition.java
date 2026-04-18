package com.archivage.metadata.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Définition d’un champ métier dans {@code document_types.custom_fields_schema}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CustomFieldDefinition(
        String key,
        String type,
        String labelFr,
        String labelPt,
        Boolean required,
        Integer maxLength
) {
}
