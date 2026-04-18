package com.archivage.document.dto;

import com.archivage.common.domain.DocumentStatus;
import jakarta.validation.constraints.NotNull;

public record DocumentStatusUpdateRequest(
        @NotNull DocumentStatus status
) {
}
