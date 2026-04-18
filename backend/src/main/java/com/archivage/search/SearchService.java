package com.archivage.search;

import com.archivage.audit.AuditService;
import com.archivage.auth.security.UserPrincipal;
import com.archivage.common.dto.PageResponseDto;
import com.archivage.common.exception.ApiException;
import com.archivage.document.entity.Document;
import com.archivage.document.repository.DocumentRepository;
import com.archivage.search.dto.SearchHitRow;
import com.archivage.search.dto.SearchRequest;
import com.archivage.search.dto.SearchResultDto;
import com.archivage.user.entity.User;
import com.archivage.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SearchService {

    private final DocumentRepository documentRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public PageResponseDto<SearchResultDto> search(SearchRequest request, UserPrincipal principal) {
        User reader = userRepository.findWithDepartmentById(principal.getUser().getId())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Utilisateur introuvable"));
        Pageable pageable = PageRequest.of(request.pageIndex(), request.pageSize());
        Page<SearchHitRow> hitPage = documentRepository.searchHitRows(request, pageable, reader);

        Map<String, Object> details = new LinkedHashMap<>();
        if (request.q() != null && !request.q().isBlank()) {
            String q = request.q().trim();
            details.put("q", q.length() > 500 ? q.substring(0, 500) + "…" : q);
        }
        details.put("page", request.pageIndex());
        details.put("size", request.pageSize());
        auditService.log("SEARCH", reader, "SEARCH", null, details);

        List<Long> ids = hitPage.getContent().stream().map(SearchHitRow::documentId).toList();
        if (ids.isEmpty()) {
            return PageResponseDto.of(new PageImpl<>(List.of(), pageable, hitPage.getTotalElements()));
        }

        Map<Long, Document> byId = documentRepository.findDetailsByIds(ids).stream()
                .collect(Collectors.toMap(Document::getId, Function.identity()));

        List<SearchResultDto> combined = new ArrayList<>(hitPage.getContent().size());
        for (SearchHitRow hit : hitPage.getContent()) {
            Document d = byId.get(hit.documentId());
            if (d != null) {
                combined.add(toResult(d, hit));
            }
        }
        return PageResponseDto.of(new PageImpl<>(combined, pageable, hitPage.getTotalElements()));
    }

    private SearchResultDto toResult(Document d, SearchHitRow hit) {
        String hlTitle = hit.highlightTitle();
        if (hlTitle != null && hlTitle.isBlank()) {
            hlTitle = null;
        }
        String hlContent = hit.highlightContent();
        if (hlContent != null && hlContent.isBlank()) {
            hlContent = null;
        }
        return new SearchResultDto(
                d.getId(),
                d.getUuid(),
                d.getTitle(),
                d.getDocumentType().getCode(),
                d.getFolderNumber(),
                d.getDocumentDate(),
                d.getStatus(),
                hlTitle,
                hlContent,
                hit.rank()
        );
    }
}
