package com.archivage.admin.dto;

public record DocumentTypeAdminDto(
        Long id,
        String code,
        String labelFr,
        String labelPt,
        boolean active
) {
}
