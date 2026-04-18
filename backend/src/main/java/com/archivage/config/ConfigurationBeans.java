package com.archivage.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({
        AppJwtProperties.class,
        AppStorageProperties.class,
        AppOcrProperties.class,
        AppCorsProperties.class,
        AppClamavProperties.class,
        AppRateLimitProperties.class
})
public class ConfigurationBeans {
}
