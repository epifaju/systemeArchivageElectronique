package com.archivage.search.saved.dto;

import jakarta.validation.constraints.Size;

import java.util.Map;

public record UpdateSavedSearchRequest(
        @Size(max = 200) String name,
        Map<String, Object> criteria
) {
}
