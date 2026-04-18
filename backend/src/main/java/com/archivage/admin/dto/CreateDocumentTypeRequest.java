package com.archivage.admin.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateDocumentTypeRequest(
        @NotBlank @Size(max = 50) String code,
        @NotBlank @Size(max = 200) String labelFr,
        @NotBlank @Size(max = 200) String labelPt,
        boolean active,
        JsonNode customFieldsSchema
) {
}
