package com.archivage.admin.dto;

import com.archivage.common.domain.OcrJobStatus;

import java.time.Instant;

public record OcrJobAdminDto(
        Long id,
        Long documentId,
        OcrJobStatus status,
        Instant startedAt,
        Instant completedAt,
        Integer retryCount,
        String errorMessage
) {
}
