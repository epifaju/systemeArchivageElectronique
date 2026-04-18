package com.archivage.document.dto;

import com.archivage.common.domain.ConfidentialityLevel;
import com.archivage.common.domain.DocumentLanguage;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

public record UploadRequest(
        @NotBlank @Size(max = 500) String title,
        @NotNull Long documentTypeId,
        @NotBlank @Size(max = 100) String folderNumber,
        @NotNull LocalDate documentDate,
        @NotNull DocumentLanguage language,
        @NotNull ConfidentialityLevel confidentialityLevel,
        Long departmentId,
        @Size(max = 100) String externalReference,
        @Size(max = 200) String author,
        String notes,
        List<@Size(max = 100) String> tags
) {
}
