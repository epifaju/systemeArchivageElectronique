package com.archivage.search.saved.dto;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record SavedSearchDto(
        Long id,
        UUID uuid,
        String name,
        Map<String, Object> criteria,
        Instant createdAt,
        Instant updatedAt
) {
}
