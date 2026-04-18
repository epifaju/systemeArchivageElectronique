package com.archivage.metadata.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CustomFieldsSchemaRoot(
        Integer version,
        List<CustomFieldDefinition> fields
) {
}
