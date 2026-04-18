package com.archivage.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.rate-limit.auth")
public record AppRateLimitProperties(
        /** Requêtes max par fenêtre (par adresse IP). */
        int maxRequests,
        /** Fenêtre en secondes. */
        int windowSeconds
) {
}
