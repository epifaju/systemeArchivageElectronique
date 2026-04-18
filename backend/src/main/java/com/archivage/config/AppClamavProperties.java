package com.archivage.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.clamav")
public record AppClamavProperties(
        boolean enabled,
        String host,
        int port,
        int connectTimeoutMs,
        int readTimeoutMs
) {
}
