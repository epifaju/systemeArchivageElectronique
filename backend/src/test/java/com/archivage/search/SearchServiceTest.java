package com.archivage.search;

import com.archivage.audit.AuditService;
import com.archivage.auth.security.UserPrincipal;
import com.archivage.common.domain.ConfidentialityLevel;
import com.archivage.common.exception.ApiException;
import com.archivage.common.domain.DocumentLanguage;
import com.archivage.common.domain.DocumentStatus;
import com.archivage.common.domain.Role;
import com.archivage.document.entity.Document;
import com.archivage.document.repository.DocumentRepository;
import com.archivage.metadata.entity.DocumentTypeEntity;
import com.archivage.search.dto.SearchHitRow;
import com.archivage.search.dto.SearchRequest;
import com.archivage.user.entity.User;
import com.archivage.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SearchServiceTest {

    @Mock
    private DocumentRepository documentRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private AuditService auditService;

    private SearchService searchService;

    @BeforeEach
    void setUp() {
        searchService = new SearchService(documentRepository, userRepository, auditService);
    }

    @Test
    void search_emptyHits_returnsEmptyPageAndAudits() {
        User reader = User.builder().username("r").passwordHash("x").role(Role.LECTEUR).active(true).build();
        reader.setId(1L);
        UserPrincipal principal = new UserPrincipal(reader);

        SearchRequest req = new SearchRequest(
                "x", null, null, null, null, null, null, null, null,
                SearchRequest.SearchSort.RELEVANCE, 0, 20
        );

        when(userRepository.findWithDepartmentById(1L)).thenReturn(Optional.of(reader));
        when(documentRepository.searchHitRows(eq(req), any(), eq(reader)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        var page = searchService.search(req, principal);

        assertThat(page.content()).isEmpty();
        verify(auditService).log(eq("SEARCH"), eq(reader), eq("SEARCH"), eq(null), any());
    }

    @Test
    void search_withHit_hydratesDocument() {
        User reader = User.builder().username("r").passwordHash("x").role(Role.LECTEUR).active(true).build();
        reader.setId(1L);
        UserPrincipal principal = new UserPrincipal(reader);

        SearchRequest req = new SearchRequest(
                null, null, null, null, null, null, null, null, null,
                SearchRequest.SearchSort.DATE_DESC, 0, 10
        );

        DocumentTypeEntity type = DocumentTypeEntity.builder()
                .code("CODE")
                .labelFr("L")
                .labelPt("L")
                .active(true)
                .build();
        type.setId(1L);

        Document doc = Document.builder()
                .title("Titre")
                .documentType(type)
                .folderNumber("F1")
                .documentDate(LocalDate.now())
                .status(DocumentStatus.VALIDATED)
                .language(DocumentLanguage.FRENCH)
                .confidentialityLevel(ConfidentialityLevel.INTERNAL)
                .build();
        doc.setId(42L);
        doc.setUuid(UUID.randomUUID());

        SearchHitRow hit = new SearchHitRow(42L, 2.0, "<em>Titre</em>", "snippet");

        when(userRepository.findWithDepartmentById(1L)).thenReturn(Optional.of(reader));
        when(documentRepository.searchHitRows(eq(req), any(), eq(reader)))
                .thenReturn(new PageImpl<>(List.of(hit), PageRequest.of(0, 10), 1));
        when(documentRepository.findDetailsByIds(List.of(42L))).thenReturn(List.of(doc));

        var page = searchService.search(req, principal);

        assertThat(page.content()).hasSize(1);
        assertThat(page.content().getFirst().id()).isEqualTo(42L);
        assertThat(page.content().getFirst().highlightTitle()).isEqualTo("<em>Titre</em>");
    }

    @Test
    void exportCsv_empty_returnsHeaderOnly() {
        User reader = User.builder().username("r").passwordHash("x").role(Role.LECTEUR).active(true).build();
        reader.setId(1L);
        UserPrincipal principal = new UserPrincipal(reader);

        SearchRequest req = new SearchRequest(
                null, null, null, null, null, null, null, null, null,
                SearchRequest.SearchSort.DATE_DESC, 0, 100
        );

        when(userRepository.findWithDepartmentById(1L)).thenReturn(Optional.of(reader));
        when(documentRepository.searchDocuments(eq(req), any(), eq(reader)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 100), 0));

        byte[] csv = searchService.exportCsv(req, 100, principal);

        assertThat(new String(csv, java.nio.charset.StandardCharsets.UTF_8)).contains("id;uuid;titre");
        verify(auditService).log(eq("SEARCH_EXPORT"), eq(reader), eq("SEARCH"), eq(null), any());
    }

    @Test
    void exportCsv_readerNotInDatabase_throwsUnauthorized() {
        User u = User.builder().username("gone").passwordHash("x").role(Role.LECTEUR).active(true).build();
        u.setId(77L);
        UserPrincipal principal = new UserPrincipal(u);

        when(userRepository.findWithDepartmentById(77L)).thenReturn(Optional.empty());

        SearchRequest req = new SearchRequest(
                null, null, null, null, null, null, null, null, null,
                SearchRequest.SearchSort.DATE_DESC, 0, 100
        );

        assertThatThrownBy(() -> searchService.exportCsv(req, 50, principal))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException e = (ApiException) ex;
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
                    assertThat(e.getCode()).isEqualTo("UNAUTHORIZED");
                });
    }

    @Test
    void search_readerNotInDatabase_throwsUnauthorized() {
        User u = User.builder().username("gone").passwordHash("x").role(Role.LECTEUR).active(true).build();
        u.setId(88L);
        UserPrincipal principal = new UserPrincipal(u);

        when(userRepository.findWithDepartmentById(88L)).thenReturn(Optional.empty());

        SearchRequest req = new SearchRequest(
                null, null, null, null, null, null, null, null, null,
                SearchRequest.SearchSort.RELEVANCE, 0, 20
        );

        assertThatThrownBy(() -> searchService.search(req, principal))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException e = (ApiException) ex;
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
                    assertThat(e.getCode()).isEqualTo("UNAUTHORIZED");
                });
    }

    @Test
    void exportCsv_clampsMaxRequestedToTenThousand() {
        User reader = User.builder().username("r").passwordHash("x").role(Role.LECTEUR).active(true).build();
        reader.setId(1L);
        UserPrincipal principal = new UserPrincipal(reader);

        SearchRequest req = new SearchRequest(
                null, null, null, null, null, null, null, null, null,
                SearchRequest.SearchSort.DATE_DESC, 0, 100
        );

        DocumentTypeEntity type = type();
        Document d1 = minimalDoc(1L, type);
        Document d2 = minimalDoc(2L, type);

        when(userRepository.findWithDepartmentById(1L)).thenReturn(Optional.of(reader));
        when(documentRepository.searchDocuments(eq(req), any(Pageable.class), eq(reader)))
                .thenReturn(new PageImpl<>(List.of(d1, d2), PageRequest.of(0, 100), 2));
        when(documentRepository.findDetailsByIds(any())).thenReturn(List.of(d1, d2));

        searchService.exportCsv(req, 99_999, principal);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> cap = ArgumentCaptor.forClass(Map.class);
        verify(auditService).log(eq("SEARCH_EXPORT"), eq(reader), eq("SEARCH"), eq(null), cap.capture());
        assertThat(cap.getValue().get("exported")).isEqualTo(2);
        assertThat(cap.getValue().get("maxRequested")).isEqualTo(10_000);
    }

    @Test
    void exportCsv_paginatesUntilCap() {
        User reader = User.builder().username("r").passwordHash("x").role(Role.LECTEUR).active(true).build();
        reader.setId(1L);
        UserPrincipal principal = new UserPrincipal(reader);

        SearchRequest base = new SearchRequest(
                null, null, null, null, null, null, null, null, null,
                SearchRequest.SearchSort.DATE_DESC, 0, 100
        );

        DocumentTypeEntity type = type();

        when(userRepository.findWithDepartmentById(1L)).thenReturn(Optional.of(reader));
        when(documentRepository.searchDocuments(any(SearchRequest.class), any(Pageable.class), eq(reader)))
                .thenAnswer(inv -> {
                    SearchRequest sr = inv.getArgument(0);
                    Pageable p = inv.getArgument(1);
                    int page = sr.page() == null ? 0 : sr.page();
                    if (page == 0) {
                        return new PageImpl<>(docs(100, 1L, type), p, 250);
                    }
                    if (page == 1) {
                        return new PageImpl<>(docs(100, 101L, type), p, 250);
                    }
                    if (page == 2) {
                        return new PageImpl<>(docs(50, 201L, type), p, 250);
                    }
                    return new PageImpl<>(List.of(), p, 0);
                });
        when(documentRepository.findDetailsByIds(any())).thenAnswer(inv -> {
            List<Long> ids = inv.getArgument(0);
            return ids.stream().map(id -> minimalDoc(id, type)).toList();
        });

        searchService.exportCsv(base, 250, principal);

        verify(documentRepository, times(3)).searchDocuments(any(SearchRequest.class), any(Pageable.class), eq(reader));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> cap = ArgumentCaptor.forClass(Map.class);
        verify(auditService).log(eq("SEARCH_EXPORT"), eq(reader), eq("SEARCH"), eq(null), cap.capture());
        assertThat(cap.getValue().get("exported")).isEqualTo(250);
        assertThat(cap.getValue().get("maxRequested")).isEqualTo(250);
    }

    private static DocumentTypeEntity type() {
        DocumentTypeEntity t = DocumentTypeEntity.builder()
                .code("CODE")
                .labelFr("L")
                .labelPt("L")
                .active(true)
                .build();
        t.setId(1L);
        return t;
    }

    private static Document minimalDoc(long id, DocumentTypeEntity type) {
        Document d = Document.builder()
                .title("T" + id)
                .documentType(type)
                .folderNumber("F")
                .documentDate(LocalDate.now())
                .status(DocumentStatus.VALIDATED)
                .language(com.archivage.common.domain.DocumentLanguage.FRENCH)
                .confidentialityLevel(ConfidentialityLevel.INTERNAL)
                .build();
        d.setId(id);
        d.setUuid(UUID.randomUUID());
        return d;
    }

    private static List<Document> docs(int count, long idStart, DocumentTypeEntity type) {
        List<Document> out = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            out.add(minimalDoc(idStart + i, type));
        }
        return out;
    }
}
