package com.archivage.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.ocr")
public record AppOcrProperties(
        int workers,
        String langDefault,
        int timeoutMinutes,
        int maxRetries,
        /** Si true, pas d'appel ocrmypdf (développement local sans binaire). */
        boolean mock,
        /**
         * Si true, passe les images raster (JPEG/PNG/TIFF…) par ImageMagick avant ocrmypdf
         * (orientation, deskew, normalisation) — utile pour scans bruyants ; désactivé par défaut.
         */
        boolean imagemagickPreprocessEnabled
) {
}
