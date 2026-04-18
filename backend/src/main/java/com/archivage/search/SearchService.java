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
import java.util.Objects;
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

    /**
     * Exporte les documents correspondant aux mêmes critères que la recherche, jusqu’à {@code maxRows} lignes (plafond 10 000).
     */
    @Transactional(readOnly = true)
    public byte[] exportCsv(SearchRequest request, int maxRows, UserPrincipal principal) {
        User reader = userRepository.findWithDepartmentById(principal.getUser().getId())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Utilisateur introuvable"));
        int cap = Math.min(Math.max(maxRows, 1), 10_000);
        List<Document> collected = new ArrayList<>();
        int pageIdx = 0;
        final int batch = 100;
        while (collected.size() < cap) {
            SearchRequest pageReq = new SearchRequest(
                    request.q(),
                    request.documentTypeId(),
                    request.dateFrom(),
                    request.dateTo(),
                    request.folderNumber(),
                    request.language(),
                    request.status(),
                    request.departmentId(),
                    request.confidentialityLevel(),
                    request.sort(),
                    pageIdx,
                    batch
            );
            Page<Document> p = documentRepository.searchDocuments(pageReq, PageRequest.of(pageIdx, batch), reader);
            if (p.isEmpty()) {
                break;
            }
            for (Document d : p.getContent()) {
                if (collected.size() >= cap) {
                    break;
                }
                collected.add(d);
            }
            if (!p.hasNext()) {
                break;
            }
            pageIdx++;
        }

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("exported", collected.size());
        details.put("maxRequested", cap);
        if (request.q() != null && !request.q().isBlank()) {
            String q = request.q().trim();
            details.put("q", q.length() > 500 ? q.substring(0, 500) + "…" : q);
        }
        auditService.log("SEARCH_EXPORT", reader, "SEARCH", null, details);
        if (collected.isEmpty()) {
            return SearchCsvExporter.toBytes(collected);
        }
        List<Long> ids = collected.stream().map(Document::getId).toList();
        Map<Long, Document> byId = documentRepository.findDetailsByIds(ids).stream()
                .collect(Collectors.toMap(Document::getId, Function.identity()));
        List<Document> ordered = ids.stream().map(byId::get).filter(Objects::nonNull).toList();
        return SearchCsvExporter.toBytes(ordered);
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
