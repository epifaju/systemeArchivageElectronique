package com.archivage.dashboard;

import com.archivage.auth.security.UserPrincipal;
import com.archivage.common.domain.ConfidentialityLevel;
import com.archivage.common.domain.DocumentLanguage;
import com.archivage.common.domain.DocumentStatus;
import com.archivage.common.domain.Role;
import com.archivage.document.entity.Document;
import com.archivage.document.policy.DocumentAccessService;
import com.archivage.document.repository.DocumentRepository;
import com.archivage.metadata.entity.DocumentTypeEntity;
import com.archivage.ocr.OcrJobService;
import com.archivage.ocr.dto.OcrQueueStatsDto;
import com.archivage.user.entity.User;
import com.archivage.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private DocumentRepository documentRepository;
    @Mock
    private OcrJobService ocrJobService;

    @Mock
    private EntityManager entityManager;

    @Mock
    private Query nativeQuery;

    private DashboardService dashboardService;

    @BeforeEach
    void setUp() throws Exception {
        dashboardService = new DashboardService(
                userRepository,
                documentRepository,
                ocrJobService,
                new DocumentAccessService()
        );
        Field em = DashboardService.class.getDeclaredField("entityManager");
        em.setAccessible(true);
        em.set(dashboardService, entityManager);
    }

    @Test
    void home_auditeur_noRecentBlocks_noOcrStats() {
        User auditeur = User.builder()
                .username("audit")
                .passwordHash("x")
                .role(Role.AUDITEUR)
                .active(true)
                .build();
        auditeur.setId(3L);
        UserPrincipal principal = new UserPrincipal(auditeur);

        when(userRepository.findWithDepartmentById(3L)).thenReturn(Optional.of(auditeur));
        when(documentRepository.countVisibleForReader(auditeur)).thenReturn(0L);
        when(documentRepository.countByStatusVisibleForReader(auditeur)).thenReturn(Collections.emptyMap());
        when(documentRepository.countCreatedSinceVisibleForReader(eq(auditeur), any(Instant.class))).thenReturn(0L);

        var home = dashboardService.home(principal);

        assertThat(home.totalDocuments()).isZero();
        assertThat(home.ocrQueue()).isNull();
        assertThat(home.recentDocuments()).isEmpty();
        assertThat(home.recentActivity()).isEmpty();
    }

    @Test
    void home_archivist_loadsRecentOcrAndActivity() throws Exception {
        User arch = User.builder()
                .username("arch")
                .fullName("Archie")
                .passwordHash("x")
                .role(Role.ARCHIVISTE)
                .active(true)
                .build();
        arch.setId(2L);
        UserPrincipal principal = new UserPrincipal(arch);

        when(userRepository.findWithDepartmentById(2L)).thenReturn(Optional.of(arch));
        when(documentRepository.countVisibleForReader(arch)).thenReturn(5L);
        Map<DocumentStatus, Long> byStatus = new EnumMap<>(DocumentStatus.class);
        for (DocumentStatus s : DocumentStatus.values()) {
            byStatus.put(s, 1L);
        }
        when(documentRepository.countByStatusVisibleForReader(arch)).thenReturn(byStatus);
        when(documentRepository.countCreatedSinceVisibleForReader(eq(arch), any(Instant.class))).thenReturn(2L);
        when(ocrJobService.getQueueStats()).thenReturn(new OcrQueueStatsDto(1, 0, 0, 0));

        DocumentTypeEntity type = DocumentTypeEntity.builder()
                .code("INV")
                .labelFr("Inv")
                .labelPt("Inv")
                .active(true)
                .build();
        type.setId(1L);
        Document doc = Document.builder()
                .title("Doc1")
                .documentType(type)
                .folderNumber("F-1")
                .documentDate(LocalDate.now())
                .status(DocumentStatus.PENDING)
                .language(DocumentLanguage.FRENCH)
                .confidentialityLevel(ConfidentialityLevel.INTERNAL)
                .build();
        doc.setId(10L);
        when(documentRepository.findRecentVisibleForReader(arch, 8)).thenReturn(List.of(doc));

        when(entityManager.createNativeQuery(anyString())).thenReturn(nativeQuery);
        Instant at = Instant.parse("2024-06-01T10:00:00Z");
        List<Object[]> activityRows = new ArrayList<>();
        activityRows.add(new Object[]{11L, "DOCUMENT_UPDATE", Instant.parse("2024-06-02T12:00:00Z"), "Doc2"});
        activityRows.add(new Object[]{10L, "DOCUMENT_VIEW", Timestamp.from(at), "Doc1"});
        when(nativeQuery.getResultList()).thenReturn(activityRows);

        var home = dashboardService.home(principal);

        assertThat(home.welcomeName()).isEqualTo("Archie");
        assertThat(home.ocrQueue()).isNotNull();
        assertThat(home.ocrQueue().pending()).isEqualTo(1L);
        assertThat(home.recentDocuments()).hasSize(1);
        assertThat(home.recentDocuments().getFirst().title()).isEqualTo("Doc1");
        assertThat(home.recentActivity()).hasSize(2);
        assertThat(home.recentActivity().getFirst().documentId()).isEqualTo(11L);
        assertThat(home.recentActivity().getFirst().action()).isEqualTo("DOCUMENT_UPDATE");
    }

    @Test
    void home_lecteur_blankFullName_usesUsername() {
        User lec = User.builder()
                .username("bob")
                .fullName("   ")
                .passwordHash("x")
                .role(Role.LECTEUR)
                .active(true)
                .build();
        lec.setId(4L);
        UserPrincipal principal = new UserPrincipal(lec);

        when(userRepository.findWithDepartmentById(4L)).thenReturn(Optional.of(lec));
        when(documentRepository.countVisibleForReader(lec)).thenReturn(0L);
        when(documentRepository.countByStatusVisibleForReader(lec)).thenReturn(Collections.emptyMap());
        when(documentRepository.countCreatedSinceVisibleForReader(eq(lec), any(Instant.class))).thenReturn(0L);
        when(documentRepository.findRecentVisibleForReader(lec, 8)).thenReturn(List.of());
        when(entityManager.createNativeQuery(anyString())).thenReturn(nativeQuery);
        when(nativeQuery.getResultList()).thenReturn(List.of());

        var home = dashboardService.home(principal);

        assertThat(home.welcomeName()).isEqualTo("bob");
        assertThat(home.ocrQueue()).isNull();
    }
}
