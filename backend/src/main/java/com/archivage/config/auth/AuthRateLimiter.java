package com.archivage.config.auth;

import com.archivage.config.AppRateLimitProperties;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Limite les POST /api/auth/login et /api/auth/refresh par adresse IP (PRD : Bucket4j).
 */
@Component
public class AuthRateLimiter {

    private final AppRateLimitProperties props;
    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();

    public AuthRateLimiter(AppRateLimitProperties props) {
        this.props = props;
    }

    /**
     * @return true si la requête est autorisée
     */
    public boolean tryConsume(String key) {
        Bucket bucket = buckets.computeIfAbsent(key, k -> newBucket());
        return bucket.tryConsume(1);
    }

    private Bucket newBucket() {
        Bandwidth limit = Bandwidth.builder()
                .capacity(props.maxRequests())
                .refillGreedy(props.maxRequests(), Duration.ofSeconds(props.windowSeconds()))
                .build();
        return Bucket.builder()
                .addLimit(limit)
                .build();
    }
}
