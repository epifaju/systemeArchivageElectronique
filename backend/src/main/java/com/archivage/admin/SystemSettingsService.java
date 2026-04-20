package com.archivage.admin;

import com.archivage.admin.dto.SystemSettingsDto;
import com.archivage.config.AppClamavProperties;
import com.archivage.config.AppCorsProperties;
import com.archivage.config.AppJwtProperties;
import com.archivage.config.AppOcrProperties;
import com.archivage.config.AppRateLimitProperties;
import com.archivage.config.AppStorageProperties;
import com.archivage.config.WatchedIngestProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SystemSettingsService {

    private final AppJwtProperties jwtProperties;
    private final AppOcrProperties ocrProperties;
    private final AppStorageProperties storageProperties;
    private final AppClamavProperties clamavProperties;
    private final AppCorsProperties corsProperties;
    private final AppRateLimitProperties rateLimitProperties;
    private final WatchedIngestProperties ingestWatchProperties;
    private final Environment environment;

    @Value("${spring.application.name}")
    private String applicationName;

    @Value("${server.port}")
    private String serverPort;

    @Value("${spring.servlet.multipart.max-file-size}")
    private String multipartMaxFileSize;

    @Value("${spring.servlet.multipart.max-request-size}")
    private String multipartMaxRequestSize;

    public SystemSettingsDto get() {
        String[] active = environment.getActiveProfiles();
        List<String> profiles = active.length > 0
                ? Arrays.asList(active)
                : Arrays.asList(environment.getDefaultProfiles());

        var jwt = new SystemSettingsDto.JwtSettingsDto(
                jwtProperties.accessTokenMinutes(),
                jwtProperties.refreshTokenDays()
        );
        var ocr = new SystemSettingsDto.OcrSettingsDto(
                ocrProperties.workers(),
                ocrProperties.langDefault(),
                ocrProperties.timeoutMinutes(),
                ocrProperties.maxRetries(),
                ocrProperties.mock(),
                ocrProperties.imagemagickPreprocessEnabled()
        );
        var storage = new SystemSettingsDto.StorageSettingsDto(storageProperties.rootPath());
        var clamav = new SystemSettingsDto.ClamavSettingsDto(
                clamavProperties.enabled(),
                clamavProperties.host(),
                clamavProperties.port(),
                clamavProperties.connectTimeoutMs(),
                clamavProperties.readTimeoutMs()
        );
        var origins = corsProperties.allowedOrigins() != null
                ? corsProperties.allowedOrigins()
                : List.<String>of();
        var rate = new SystemSettingsDto.RateLimitSettingsDto(
                rateLimitProperties.maxRequests(),
                rateLimitProperties.windowSeconds()
        );

        String docDate = ingestWatchProperties.getDocumentDate() != null
                ? ingestWatchProperties.getDocumentDate().toString()
                : null;

        var ingest = new SystemSettingsDto.IngestWatchSettingsDto(
                ingestWatchProperties.isEnabled(),
                ingestWatchProperties.getDirectory(),
                ingestWatchProperties.getIntervalMs(),
                ingestWatchProperties.getUserId(),
                ingestWatchProperties.getDocumentTypeId(),
                ingestWatchProperties.getFolderNumber(),
                ingestWatchProperties.getTitlePrefix(),
                docDate,
                ingestWatchProperties.getLanguage() != null
                        ? ingestWatchProperties.getLanguage().name()
                        : null,
                ingestWatchProperties.getConfidentiality() != null
                        ? ingestWatchProperties.getConfidentiality().name()
                        : null,
                ingestWatchProperties.getDepartmentId(),
                ingestWatchProperties.getExternalReference(),
                ingestWatchProperties.getAuthor(),
                ingestWatchProperties.getNotes(),
                ingestWatchProperties.getTags() != null
                        ? List.copyOf(ingestWatchProperties.getTags())
                        : List.of()
        );

        return new SystemSettingsDto(
                applicationName,
                profiles,
                serverPort,
                multipartMaxFileSize,
                multipartMaxRequestSize,
                jwt,
                ocr,
                storage,
                clamav,
                origins,
                rate,
                ingest
        );
    }
}
