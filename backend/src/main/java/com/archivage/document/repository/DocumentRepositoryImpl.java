package com.archivage.document.repository;

import com.archivage.common.domain.DocumentStatus;
import com.archivage.document.entity.Document;
import com.archivage.document.policy.DocumentAccessService;
import com.archivage.search.dto.SearchHitRow;
import com.archivage.search.dto.SearchRequest;
import com.archivage.user.entity.User;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DocumentRepositoryImpl implements DocumentRepositoryCustom {

    private static final String HEADLINE_OPTS = "'StartSel=<mark>, StopSel=</mark>, MaxWords=28, MinWords=6'";
    private static final String HEADLINE_TITLE_OPTS = "'StartSel=<mark>, StopSel=</mark>, MaxWords=18, MinWords=4'";

    @PersistenceContext
    private EntityManager entityManager;

    private final DocumentAccessService documentAccessService;

    public DocumentRepositoryImpl(DocumentAccessService documentAccessService) {
        this.documentAccessService = documentAccessService;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Page<Document> searchDocuments(SearchRequest request, Pageable pageable, User reader) {
        Map<String, Object> params = new HashMap<>();
        StringBuilder where = new StringBuilder(" WHERE d.is_deleted = false ");
        appendSearchWhere(where, params, request, reader);

        String orderBy = buildOrderBy(request);
        String baseFrom = " FROM documents d " + where;

        Query countQ = entityManager.createNativeQuery("SELECT count(*) " + baseFrom);
        bindParams(countQ, params);
        long total = ((Number) countQ.getSingleResult()).longValue();

        String select = "SELECT d.* " + baseFrom + " ORDER BY " + orderBy;
        Query dataQ = entityManager.createNativeQuery(select, Document.class);
        bindParams(dataQ, params);
        dataQ.setFirstResult((int) pageable.getOffset());
        dataQ.setMaxResults(pageable.getPageSize());

        List<Document> list = dataQ.getResultList();
        return new PageImpl<>(list, pageable, total);
    }

    @Override
    public Page<SearchHitRow> searchHitRows(SearchRequest request, Pageable pageable, User reader) {
        Map<String, Object> params = new HashMap<>();
        StringBuilder where = new StringBuilder(" WHERE d.is_deleted = false ");
        appendSearchWhere(where, params, request, reader);

        boolean hasQ = request.q() != null && !request.q().isBlank();
        String selectCols = hasQ
                ? """
                d.id,
                ts_rank(d.search_vector, plainto_tsquery('french', :q)),
                ts_headline('french', d.title, plainto_tsquery('french', :q), %s),
                ts_headline('french', COALESCE(d.ocr_text, ''), plainto_tsquery('french', :q), %s)
                """.formatted(HEADLINE_TITLE_OPTS, HEADLINE_OPTS)
                : """
                d.id,
                CAST(NULL AS DOUBLE PRECISION),
                CAST(NULL AS TEXT),
                CASE WHEN d.ocr_text IS NOT NULL AND length(trim(d.ocr_text)) > 0 THEN substring(d.ocr_text FROM 1 FOR 300) ELSE NULL END
                """;

        String orderBy = buildOrderBy(request);
        String baseFrom = " FROM documents d " + where;

        Query countQ = entityManager.createNativeQuery("SELECT count(*) " + baseFrom);
        bindParams(countQ, params);
        long total = ((Number) countQ.getSingleResult()).longValue();

        String select = "SELECT " + selectCols + baseFrom + " ORDER BY " + orderBy;
        Query dataQ = entityManager.createNativeQuery(select);
        bindParams(dataQ, params);
        dataQ.setFirstResult((int) pageable.getOffset());
        dataQ.setMaxResults(pageable.getPageSize());

        List<?> rawList = dataQ.getResultList();
        List<SearchHitRow> rows = new ArrayList<>(rawList.size());
        for (Object o : rawList) {
            Object[] row = (Object[]) o;
            Long id = ((Number) row[0]).longValue();
            Double rank = row[1] == null ? null : ((Number) row[1]).doubleValue();
            String hlTitle = row[2] != null ? row[2].toString() : null;
            String hlContent = row[3] != null ? row[3].toString() : null;
            rows.add(new SearchHitRow(id, rank, hlTitle, hlContent));
        }
        return new PageImpl<>(rows, pageable, total);
    }

    @Override
    public long countVisibleForReader(User reader) {
        Map<String, Object> params = new HashMap<>();
        StringBuilder where = new StringBuilder();
        appendReaderDocumentScope(where, params, reader);
        Query q = entityManager.createNativeQuery("SELECT count(*) FROM documents d " + where);
        bindParams(q, params);
        return ((Number) q.getSingleResult()).longValue();
    }

    @Override
    public Map<DocumentStatus, Long> countByStatusVisibleForReader(User reader) {
        Map<String, Object> params = new HashMap<>();
        StringBuilder where = new StringBuilder();
        appendReaderDocumentScope(where, params, reader);
        Query q = entityManager.createNativeQuery(
                "SELECT d.status, COUNT(*) FROM documents d " + where + " GROUP BY d.status"
        );
        bindParams(q, params);
        List<?> rawList = q.getResultList();
        EnumMap<DocumentStatus, Long> map = new EnumMap<>(DocumentStatus.class);
        for (DocumentStatus s : DocumentStatus.values()) {
            map.put(s, 0L);
        }
        for (Object o : rawList) {
            Object[] row = (Object[]) o;
            String statusName = (String) row[0];
            long c = ((Number) row[1]).longValue();
            map.put(DocumentStatus.valueOf(statusName), c);
        }
        return map;
    }

    @Override
    public long countCreatedSinceVisibleForReader(User reader, Instant since) {
        Map<String, Object> params = new HashMap<>();
        StringBuilder where = new StringBuilder();
        appendReaderDocumentScope(where, params, reader);
        where.append(" AND d.created_at >= :since ");
        params.put("since", since);
        Query q = entityManager.createNativeQuery("SELECT count(*) FROM documents d " + where);
        bindParams(q, params);
        return ((Number) q.getSingleResult()).longValue();
    }

    private void appendReaderDocumentScope(StringBuilder where, Map<String, Object> params, User reader) {
        where.append(" WHERE d.is_deleted = false ");
        documentAccessService.appendReadScope(where, params, reader);
    }

    private void appendSearchWhere(StringBuilder where, Map<String, Object> params, SearchRequest request, User reader) {
        if (request.q() != null && !request.q().isBlank()) {
            where.append(" AND d.search_vector @@ plainto_tsquery('french', :q) ");
            params.put("q", request.q().trim());
        }
        if (request.documentTypeId() != null) {
            where.append(" AND d.document_type_id = :documentTypeId ");
            params.put("documentTypeId", request.documentTypeId());
        }
        if (request.dateFrom() != null) {
            where.append(" AND d.document_date >= :dateFrom ");
            params.put("dateFrom", request.dateFrom());
        }
        if (request.dateTo() != null) {
            where.append(" AND d.document_date <= :dateTo ");
            params.put("dateTo", request.dateTo());
        }
        if (request.folderNumber() != null && !request.folderNumber().isBlank()) {
            where.append(" AND d.folder_number ILIKE :folderPat ");
            params.put("folderPat", "%" + request.folderNumber().trim() + "%");
        }
        if (request.language() != null) {
            where.append(" AND d.language = :language ");
            params.put("language", request.language().name());
        }
        if (request.status() != null) {
            where.append(" AND d.status = :status ");
            params.put("status", request.status().name());
        }
        if (request.departmentId() != null) {
            where.append(" AND d.department_id = :departmentId ");
            params.put("departmentId", request.departmentId());
        }
        if (request.confidentialityLevel() != null) {
            where.append(" AND d.confidentiality_level = :confidentialityLevel ");
            params.put("confidentialityLevel", request.confidentialityLevel().name());
        }

        documentAccessService.appendReadScope(where, params, reader);
    }

    private static String buildOrderBy(SearchRequest request) {
        boolean hasQ = request.q() != null && !request.q().isBlank();
        SearchRequest.SearchSort sort = request.sort();
        if (hasQ && (sort == null || sort == SearchRequest.SearchSort.RELEVANCE)) {
            return " ts_rank(d.search_vector, plainto_tsquery('french', :q)) DESC NULLS LAST, d.updated_at DESC ";
        }
        if (sort == null) {
            return " d.document_date DESC ";
        }
        return switch (sort) {
            case DATE_ASC -> " d.document_date ASC ";
            case DATE_DESC -> " d.document_date DESC ";
            case TITLE_ASC -> " d.title ASC ";
            case RELEVANCE -> " d.updated_at DESC ";
        };
    }

    private static void bindParams(Query q, Map<String, Object> params) {
        for (Map.Entry<String, Object> e : params.entrySet()) {
            q.setParameter(e.getKey(), e.getValue());
        }
    }
}
