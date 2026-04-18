package com.archivage.dashboard.dto;

import java.time.Instant;

/**
 * Dernière activité utilisateur sur un document (audit), pour le tableau de bord (PRD P1).
 */
public record DashboardRecentActivityDto(
        Long documentId,
        String action,
        Instant occurredAt,
        String documentTitle
) {
}
