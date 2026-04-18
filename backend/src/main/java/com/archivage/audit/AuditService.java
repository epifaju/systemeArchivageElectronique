package com.archivage.audit;

import com.archivage.audit.entity.AuditLog;
import com.archivage.audit.repository.AuditLogRepository;
import com.archivage.user.entity.User;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(String action, User user, String resourceType, Long resourceId, Map<String, Object> details) {
        AuditLog entry = AuditLog.builder()
                .user(user)
                .action(action)
                .resourceType(resourceType)
                .resourceId(resourceId)
                .details(details != null ? details : new HashMap<>())
                .ipAddress(resolveIp())
                .userAgent(resolveUserAgent())
                .createdAt(Instant.now())
                .build();
        auditLogRepository.save(entry);
    }

    public void logLoginSuccess(User user) {
        log("LOGIN_SUCCESS", user, "AUTH", user.getId(), Map.of("username", user.getUsername()));
    }

    public void logLoginFailure(String username) {
        log("LOGIN_FAILURE", null, "AUTH", null, Map.of("username", username));
    }

    public void logLogout(User user) {
        log("LOGOUT", user, "AUTH", user.getId(), Map.of());
    }

    private String resolveIp() {
        return Optional.ofNullable(RequestContextHolder.getRequestAttributes())
                .filter(ServletRequestAttributes.class::isInstance)
                .map(ServletRequestAttributes.class::cast)
                .map(ServletRequestAttributes::getRequest)
                .map(HttpServletRequest::getRemoteAddr)
                .orElse(null);
    }

    private String resolveUserAgent() {
        return Optional.ofNullable(RequestContextHolder.getRequestAttributes())
                .filter(ServletRequestAttributes.class::isInstance)
                .map(ServletRequestAttributes.class::cast)
                .map(ServletRequestAttributes::getRequest)
                .map(r -> r.getHeader("User-Agent"))
                .orElse(null);
    }
}
