package com.archivage.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.ocr")
public record AppOcrProperties(
        int workers,
        String langDefault,
        int timeoutMinutes,
        int maxRetries,
        /** Si true, pas d'appel ocrmypdf (développement local sans binaire). */
        boolean mock
) {
}
