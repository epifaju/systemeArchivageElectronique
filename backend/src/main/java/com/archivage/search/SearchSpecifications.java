package com.archivage.search;

import com.archivage.document.entity.Document;
import com.archivage.search.dto.SearchRequest;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

public final class SearchSpecifications {

    private SearchSpecifications() {
    }

    public static Specification<Document> fromRequest(SearchRequest request) {
        return (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            p.add(cb.isFalse(root.get("isDeleted")));
            if (request.documentTypeId() != null) {
                p.add(cb.equal(root.get("documentType").get("id"), request.documentTypeId()));
            }
            if (request.dateFrom() != null) {
                p.add(cb.greaterThanOrEqualTo(root.get("documentDate"), request.dateFrom()));
            }
            if (request.dateTo() != null) {
                p.add(cb.lessThanOrEqualTo(root.get("documentDate"), request.dateTo()));
            }
            if (request.folderNumber() != null && !request.folderNumber().isBlank()) {
                p.add(cb.like(cb.lower(root.get("folderNumber")), "%" + request.folderNumber().toLowerCase() + "%"));
            }
            if (request.language() != null) {
                p.add(cb.equal(root.get("language"), request.language()));
            }
            if (request.status() != null) {
                p.add(cb.equal(root.get("status"), request.status()));
            }
            if (request.departmentId() != null) {
                p.add(cb.equal(root.join("department", JoinType.LEFT).get("id"), request.departmentId()));
            }
            if (request.confidentialityLevel() != null) {
                p.add(cb.equal(root.get("confidentialityLevel"), request.confidentialityLevel()));
            }
            if (p.isEmpty()) {
                return cb.conjunction();
            }
            return cb.and(p.toArray(Predicate[]::new));
        };
    }
}
