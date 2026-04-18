package com.archivage.document.repository;

import com.archivage.common.domain.DocumentStatus;
import com.archivage.document.entity.Document;
import com.archivage.search.dto.SearchHitRow;
import com.archivage.search.dto.SearchRequest;
import com.archivage.user.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.Map;

public interface DocumentRepositoryCustom {

    Page<Document> searchDocuments(SearchRequest request, Pageable pageable, User reader);

    Page<SearchHitRow> searchHitRows(SearchRequest request, Pageable pageable, User reader);

    long countVisibleForReader(User reader);

    Map<DocumentStatus, Long> countByStatusVisibleForReader(User reader);

    long countCreatedSinceVisibleForReader(User reader, Instant since);
}
