package com.archivage.config.auth;

import com.archivage.config.AppRateLimitProperties;
import org.springframework.stereotype.Component;

import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

@Component
public class AuthRateLimiter {

    private final AppRateLimitProperties props;
    private final ConcurrentHashMap<String, Deque<Long>> hits = new ConcurrentHashMap<>();

    public AuthRateLimiter(AppRateLimitProperties props) {
        this.props = props;
    }

    /**
     * @return true si la requête est autorisée
     */
    public boolean tryConsume(String key) {
        long windowMs = props.windowSeconds() * 1000L;
        long now = System.currentTimeMillis();
        Deque<Long> dq = hits.computeIfAbsent(key, k -> new ConcurrentLinkedDeque<>());
        synchronized (dq) {
            while (!dq.isEmpty() && now - dq.peekFirst() > windowMs) {
                dq.pollFirst();
            }
            if (dq.size() >= props.maxRequests()) {
                return false;
            }
            dq.addLast(now);
            return true;
        }
    }
}
