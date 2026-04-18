package com.archivage.search;

import com.archivage.auth.security.UserPrincipal;
import com.archivage.common.domain.ConfidentialityLevel;
import com.archivage.common.domain.DocumentLanguage;
import com.archivage.common.domain.DocumentStatus;
import com.archivage.common.dto.PageResponseDto;
import com.archivage.search.dto.SearchRequest;
import com.archivage.search.dto.SearchResultDto;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

@RestController
@RequestMapping("/api/search")
@RequiredArgsConstructor
public class SearchController {

    private final SearchService searchService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','ARCHIVISTE','AGENT','LECTEUR')")
    public PageResponseDto<SearchResultDto> searchSimple(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Long type,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(required = false) String folderNumber,
            @RequestParam(required = false) DocumentLanguage language,
            @RequestParam(required = false) DocumentStatus status,
            @RequestParam(required = false) Long departmentId,
            @RequestParam(required = false) ConfidentialityLevel confidentialityLevel,
            @RequestParam(required = false) SearchRequest.SearchSort sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        SearchRequest req = new SearchRequest(
                q,
                type,
                dateFrom,
                dateTo,
                folderNumber,
                language,
                status,
                departmentId,
                confidentialityLevel,
                sort,
                page,
                size
        );
        return searchService.search(req, principal);
    }

    /**
     * Export CSV (mêmes filtres que {@link #searchSimple}) — UTF-8 avec BOM, séparateur « ; ».
     * Paramètre {@code max} : nombre max de lignes (défaut 5000, plafond 10 000).
     */
    @GetMapping(value = "/export.csv", produces = "text/csv; charset=UTF-8")
    @PreAuthorize("hasAnyRole('ADMIN','ARCHIVISTE','AGENT','LECTEUR')")
    public ResponseEntity<byte[]> exportCsv(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Long type,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(required = false) String folderNumber,
            @RequestParam(required = false) DocumentLanguage language,
            @RequestParam(required = false) DocumentStatus status,
            @RequestParam(required = false) Long departmentId,
            @RequestParam(required = false) ConfidentialityLevel confidentialityLevel,
            @RequestParam(required = false) SearchRequest.SearchSort sort,
            @RequestParam(defaultValue = "5000") int max,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        SearchRequest req = new SearchRequest(
                q,
                type,
                dateFrom,
                dateTo,
                folderNumber,
                language,
                status,
                departmentId,
                confidentialityLevel,
                sort,
                0,
                20
        );
        byte[] body = searchService.exportCsv(req, max, principal);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"recherche-documents.csv\"")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(body);
    }

    @PostMapping("/advanced")
    @PreAuthorize("hasAnyRole('ADMIN','ARCHIVISTE','AGENT','LECTEUR')")
    public PageResponseDto<SearchResultDto> searchAdvanced(
            @Valid @RequestBody SearchRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return searchService.search(request, principal);
    }
}
