package com.archivage.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.jwt")
public record AppJwtProperties(
        String secret,
        int accessTokenMinutes,
        int refreshTokenDays
) {
}
