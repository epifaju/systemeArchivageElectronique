package com.archivage.audit;

import com.archivage.audit.entity.AuditLog;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

public final class AuditLogSpecifications {

    private AuditLogSpecifications() {
    }

    /**
     * @param dateFrom inclusive — début du jour calendaire dans {@code zoneId}
     * @param dateTo   inclusive — fin du même jour calendaire (borne supérieure exclusive = minuit du lendemain dans {@code zoneId})
     */
    public static Specification<AuditLog> filtered(
            LocalDate dateFrom,
            LocalDate dateTo,
            String action,
            Long userId,
            String resourceType,
            ZoneId zoneId
    ) {
        ZoneId zone = zoneId != null ? zoneId : ZoneId.of("UTC");
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (dateFrom != null) {
                Instant start = dateFrom.atStartOfDay(zone).toInstant();
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), start));
            }
            if (dateTo != null) {
                Instant end = dateTo.plusDays(1).atStartOfDay(zone).toInstant();
                predicates.add(cb.lessThan(root.get("createdAt"), end));
            }
            if (action != null && !action.isBlank()) {
                predicates.add(cb.equal(root.get("action"), action.trim()));
            }
            if (userId != null) {
                predicates.add(cb.equal(root.join("user", JoinType.INNER).get("id"), userId));
            }
            if (resourceType != null && !resourceType.isBlank()) {
                predicates.add(cb.equal(root.get("resourceType"), resourceType.trim()));
            }
            if (predicates.isEmpty()) {
                return cb.conjunction();
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    /**
     * Entrées d’audit liées à un document (resource_type = DOCUMENT, resource_id = id).
     * Par défaut les consultations {@code DOCUMENT_VIEW} peuvent être exclues (fiche plus lisible).
     */
    public static Specification<AuditLog> forDocument(Long documentId, boolean includeViews) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("resourceType"), "DOCUMENT"));
            predicates.add(cb.equal(root.get("resourceId"), documentId));
            if (!includeViews) {
                predicates.add(cb.notEqual(root.get("action"), "DOCUMENT_VIEW"));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}
