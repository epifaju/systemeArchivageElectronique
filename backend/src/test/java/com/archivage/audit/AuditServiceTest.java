package com.archivage.audit;

import com.archivage.audit.entity.AuditLog;
import com.archivage.audit.repository.AuditLogRepository;
import com.archivage.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditServiceTest {

    @Mock
    private AuditLogRepository auditLogRepository;

    private AuditService auditService;

    @BeforeEach
    void setUp() {
        auditService = new AuditService(auditLogRepository);
        when(auditLogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void logLoginFailure_persistsWithUsername() {
        auditService.logLoginFailure("alice");

        ArgumentCaptor<AuditLog> cap = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(cap.capture());
        AuditLog saved = cap.getValue();
        assertThat(saved.getAction()).isEqualTo("LOGIN_FAILURE");
        assertThat(saved.getUser()).isNull();
        assertThat(saved.getDetails()).containsEntry("username", "alice");
    }

    @Test
    void logLoginSuccess_persistsWithUser() {
        User u = User.builder()
                .username("bob")
                .passwordHash("x")
                .role(com.archivage.common.domain.Role.LECTEUR)
                .active(true)
                .build();
        u.setId(7L);

        auditService.logLoginSuccess(u);

        ArgumentCaptor<AuditLog> cap = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(cap.capture());
        assertThat(cap.getValue().getAction()).isEqualTo("LOGIN_SUCCESS");
        assertThat(cap.getValue().getUser()).isSameAs(u);
    }

    @Test
    void log_customAction() {
        User u = User.builder()
                .username("c")
                .passwordHash("x")
                .role(com.archivage.common.domain.Role.ADMIN)
                .active(true)
                .build();
        u.setId(1L);

        auditService.log("X", u, "R", 2L, Map.of("k", "v"));

        ArgumentCaptor<AuditLog> cap = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(cap.capture());
        assertThat(cap.getValue().getAction()).isEqualTo("X");
        assertThat(cap.getValue().getResourceType()).isEqualTo("R");
        assertThat(cap.getValue().getResourceId()).isEqualTo(2L);
        assertThat(cap.getValue().getDetails()).containsEntry("k", "v");
    }
}
