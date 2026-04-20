package com.archivage.search.dto;

import com.archivage.common.domain.ConfidentialityLevel;
import com.archivage.common.domain.DocumentLanguage;
import com.archivage.common.domain.DocumentStatus;

import java.time.LocalDate;

public record SearchRequest(
        String q,
        Long documentTypeId,
        LocalDate dateFrom,
        LocalDate dateTo,
        String folderNumber,
        DocumentLanguage language,
        DocumentStatus status,
        Long departmentId,
        ConfidentialityLevel confidentialityLevel,
        SearchSort sort,
        Integer page,
        Integer size
) {
    public enum SearchSort {
        RELEVANCE,
        DATE_DESC,
        DATE_ASC,
        TITLE_ASC,
        /** Date d’ajout / création en base (created_at). */
        CREATED_DESC,
        CREATED_ASC
    }

    public int pageIndex() {
        return page == null ? 0 : Math.max(0, page);
    }

    public int pageSize() {
        int s = size == null ? 20 : size;
        return Math.min(Math.max(s, 1), 100);
    }
}
