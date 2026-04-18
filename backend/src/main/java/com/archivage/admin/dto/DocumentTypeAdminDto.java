package com.archivage.admin.dto;

import com.fasterxml.jackson.databind.JsonNode;

public record DocumentTypeAdminDto(
        Long id,
        String code,
        String labelFr,
        String labelPt,
        boolean active,
        JsonNode customFieldsSchema
) {
}
