package com.archivage.search.saved.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Map;

public record CreateSavedSearchRequest(
        @NotBlank @Size(max = 200) String name,
        @NotNull Map<String, Object> criteria
) {
}
