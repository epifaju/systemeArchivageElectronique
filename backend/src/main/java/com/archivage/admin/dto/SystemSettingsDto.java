package com.archivage.admin.dto;

import java.util.List;

/**
 * Paramètres d’exploitation exposés en lecture seule (aucun secret).
 */
public record SystemSettingsDto(
        String applicationName,
        List<String> activeProfiles,
        String serverPort,
        String multipartMaxFileSize,
        String multipartMaxRequestSize,
        JwtSettingsDto jwt,
        OcrSettingsDto ocr,
        StorageSettingsDto storage,
        ClamavSettingsDto clamav,
        List<String> corsAllowedOrigins,
        RateLimitSettingsDto authRateLimit,
        IngestWatchSettingsDto ingestWatch
) {
    public record JwtSettingsDto(int accessTokenMinutes, int refreshTokenDays) {}

    public record OcrSettingsDto(
            int workers,
            String langDefault,
            int timeoutMinutes,
            int maxRetries,
            boolean mock,
            boolean imagemagickPreprocessEnabled
    ) {}

    public record StorageSettingsDto(String rootPath) {}

    public record ClamavSettingsDto(
            boolean enabled,
            String host,
            int port,
            int connectTimeoutMs,
            int readTimeoutMs
    ) {}

    public record RateLimitSettingsDto(int maxRequests, int windowSeconds) {}

    public record IngestWatchSettingsDto(
            boolean enabled,
            String directory,
            long intervalMs,
            Long userId,
            Long documentTypeId,
            String folderNumber,
            String titlePrefix,
            String documentDate,
            String language,
            String confidentiality,
            Long departmentId,
            String externalReference,
            String author,
            String notes,
            List<String> tags
    ) {}
}
