package com.archivage.admin.dto;

import java.time.Instant;
import java.util.Map;

public record AuditLogAdminDto(
        Long id,
        Long userId,
        String username,
        String action,
        String resourceType,
        Long resourceId,
        Map<String, Object> details,
        String ipAddress,
        Instant createdAt
) {
}
