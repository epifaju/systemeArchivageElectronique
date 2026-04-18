package com.archivage.dashboard;

import com.archivage.auth.security.UserPrincipal;
import com.archivage.common.domain.DocumentStatus;
import com.archivage.common.domain.Role;
import com.archivage.common.exception.ApiException;
import com.archivage.dashboard.dto.DashboardRecentActivityDto;
import com.archivage.dashboard.dto.DashboardRecentDocumentDto;
import com.archivage.dashboard.dto.HomeDashboardDto;
import com.archivage.document.entity.Document;
import com.archivage.document.policy.DocumentAccessService;
import com.archivage.document.repository.DocumentRepository;
import com.archivage.ocr.OcrJobService;
import com.archivage.ocr.dto.OcrQueueStatsDto;
import com.archivage.user.entity.User;
import com.archivage.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final UserRepository userRepository;
    private final DocumentRepository documentRepository;
    private final OcrJobService ocrJobService;
    private final DocumentAccessService documentAccessService;

    @PersistenceContext
    private EntityManager entityManager;

    @Transactional(readOnly = true)
    public HomeDashboardDto home(UserPrincipal principal) {
        User reader = userRepository.findWithDepartmentById(principal.getUser().getId())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Utilisateur introuvable"));

        long total = documentRepository.countVisibleForReader(reader);
        Map<DocumentStatus, Long> byStatusEnum = documentRepository.countByStatusVisibleForReader(reader);
        Instant since = Instant.now().minus(7, ChronoUnit.DAYS);
        long last7 = documentRepository.countCreatedSinceVisibleForReader(reader, since);

        Map<String, Long> byStatus = new LinkedHashMap<>();
        for (DocumentStatus s : DocumentStatus.values()) {
            byStatus.put(s.name(), byStatusEnum.getOrDefault(s, 0L));
        }

        OcrQueueStatsDto ocr = null;
        Role r = reader.getRole();
        if (r == Role.ADMIN || r == Role.ARCHIVISTE) {
            ocr = ocrJobService.getQueueStats();
        }

        List<DashboardRecentDocumentDto> recentDocuments = List.of();
        List<DashboardRecentActivityDto> recentActivity = List.of();
        if (r != Role.AUDITEUR) {
            List<Document> recent = documentRepository.findRecentVisibleForReader(reader, 8);
            recentDocuments = recent.stream().map(d -> {
                var t = d.getDocumentType();
                return new DashboardRecentDocumentDto(
                        d.getId(),
                        d.getTitle(),
                        t.getCode(),
                        t.getLabelFr(),
                        t.getLabelPt(),
                        d.getDocumentDate(),
                        d.getStatus()
                );
            }).toList();
            recentActivity = loadRecentDocumentActivity(reader, 8);
        }

        String welcomeName = reader.getFullName() != null && !reader.getFullName().isBlank()
                ? reader.getFullName()
                : reader.getUsername();

        return new HomeDashboardDto(welcomeName, total, byStatus, last7, ocr, recentDocuments, recentActivity);
    }

    /**
     * Dernière action par document (distinct), triée par date d’action décroissante — conforme PRD « historique accès ».
     */
    @SuppressWarnings("unchecked")
    private List<DashboardRecentActivityDto> loadRecentDocumentActivity(User reader, int limit) {
        if (limit <= 0) {
            return List.of();
        }
        StringBuilder scopeWhere = new StringBuilder();
        scopeWhere.append(" WHERE al.user_id = :uid ");
        scopeWhere.append(" AND al.resource_type = 'DOCUMENT' ");
        scopeWhere.append("""
                 AND al.action IN (
                  'DOCUMENT_VIEW',
                  'DOCUMENT_UPDATE',
                  'DOCUMENT_STATUS',
                  'DOCUMENT_DOWNLOAD_ORIGINAL',
                  'DOCUMENT_DOWNLOAD_OCR',
                  'DOCUMENT_REPROCESS_OCR',
                  'DOCUMENT_UPLOAD'
                )
                """);
        Map<String, Object> scopeParams = new HashMap<>();
        scopeParams.put("uid", reader.getId());
        documentAccessService.appendReadScope(scopeWhere, scopeParams, reader);

        String sql = """
                SELECT document_id, action, occurred_at, title FROM (
                  SELECT DISTINCT ON (al.resource_id)
                    al.resource_id AS document_id,
                    al.action,
                    al.created_at AS occurred_at,
                    COALESCE(d.title, '') AS title
                  FROM audit_logs al
                  INNER JOIN documents d ON d.id = al.resource_id AND d.is_deleted = false
                """
                + scopeWhere
                + """
                  ORDER BY al.resource_id, al.created_at DESC
                ) sub
                ORDER BY sub.occurred_at DESC
                LIMIT :lim
                """;
        Query q = entityManager.createNativeQuery(sql);
        q.setParameter("uid", reader.getId());
        q.setParameter("lim", limit);
        for (Map.Entry<String, Object> e : scopeParams.entrySet()) {
            if (!"uid".equals(e.getKey())) {
                q.setParameter(e.getKey(), e.getValue());
            }
        }
        List<Object[]> rows = q.getResultList();
        List<DashboardRecentActivityDto> out = new ArrayList<>(rows.size());
        for (Object[] row : rows) {
            Number idNum = (Number) row[0];
            String action = (String) row[1];
            Instant at = null;
            if (row[2] instanceof Timestamp ts) {
                at = ts.toInstant();
            } else if (row[2] instanceof Instant i) {
                at = i;
            }
            String title = row[3] != null ? String.valueOf(row[3]) : "";
            out.add(new DashboardRecentActivityDto(idNum != null ? idNum.longValue() : null, action, at, title));
        }
        return out;
    }
}
