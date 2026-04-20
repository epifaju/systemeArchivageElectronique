package com.archivage.admin;

import com.archivage.config.AppClamavProperties;
import com.archivage.config.AppCorsProperties;
import com.archivage.config.AppJwtProperties;
import com.archivage.config.AppOcrProperties;
import com.archivage.config.AppRateLimitProperties;
import com.archivage.config.AppStorageProperties;
import com.archivage.config.WatchedIngestProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.Environment;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SystemSettingsServiceTest {

    @Mock
    private Environment environment;

    private SystemSettingsService service;

    @BeforeEach
    void setUp() {
        AppJwtProperties jwt = new AppJwtProperties("sec", 20, 14);
        AppOcrProperties ocr = new AppOcrProperties(2, "fra", 45, 3, false, true);
        AppStorageProperties storage = new AppStorageProperties("/data/store");
        AppClamavProperties clam = new AppClamavProperties(true, "127.0.0.1", 3310, 3000, 5000);
        AppCorsProperties cors = new AppCorsProperties(List.of("http://localhost:5173"));
        AppRateLimitProperties rate = new AppRateLimitProperties(60, 120);
        WatchedIngestProperties ingest = new WatchedIngestProperties();

        service = new SystemSettingsService(jwt, ocr, storage, clam, cors, rate, ingest, environment);

        ReflectionTestUtils.setField(service, "applicationName", "test-app");
        ReflectionTestUtils.setField(service, "serverPort", "8080");
        ReflectionTestUtils.setField(service, "multipartMaxFileSize", "10MB");
        ReflectionTestUtils.setField(service, "multipartMaxRequestSize", "12MB");
    }

    @Test
    void get_mapsConfigurationWithoutSecrets() {
        when(environment.getActiveProfiles()).thenReturn(new String[]{"dev"});

        var dto = service.get();

        assertThat(dto.applicationName()).isEqualTo("test-app");
        assertThat(dto.activeProfiles()).contains("dev");
        assertThat(dto.serverPort()).isEqualTo("8080");
        assertThat(dto.jwt().accessTokenMinutes()).isEqualTo(20);
        assertThat(dto.ocr().imagemagickPreprocessEnabled()).isTrue();
        assertThat(dto.storage().rootPath()).isEqualTo("/data/store");
        assertThat(dto.clamav().host()).isEqualTo("127.0.0.1");
        assertThat(dto.corsAllowedOrigins()).contains("http://localhost:5173");
        assertThat(dto.authRateLimit().maxRequests()).isEqualTo(60);
    }
}
