package com.archivage.search.dto;

/**
 * Résultat d’une ligne de recherche native (extraits + score) avant hydratation {@link com.archivage.document.entity.Document}.
 */
public record SearchHitRow(
        Long documentId,
        Double rank,
        String highlightTitle,
        String highlightContent
) {
}
