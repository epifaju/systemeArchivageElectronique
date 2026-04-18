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
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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

    @PostMapping("/advanced")
    @PreAuthorize("hasAnyRole('ADMIN','ARCHIVISTE','AGENT','LECTEUR')")
    public PageResponseDto<SearchResultDto> searchAdvanced(
            @Valid @RequestBody SearchRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return searchService.search(request, principal);
    }
}
