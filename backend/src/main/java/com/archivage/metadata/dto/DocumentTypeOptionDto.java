package com.archivage.metadata.dto;

import com.fasterxml.jackson.databind.JsonNode;

public record DocumentTypeOptionDto(
        Long id,
        String code,
        String labelFr,
        String labelPt,
        JsonNode customFieldsSchema
) {
}
