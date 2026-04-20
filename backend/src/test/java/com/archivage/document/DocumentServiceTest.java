package com.archivage.document;

import com.archivage.audit.AuditService;
import com.archivage.audit.repository.AuditLogRepository;
import com.archivage.auth.security.UserPrincipal;
import com.archivage.common.domain.ConfidentialityLevel;
import com.archivage.common.domain.DocumentLanguage;
import com.archivage.common.domain.DocumentStatus;
import com.archivage.common.domain.Role;
import com.archivage.common.exception.ApiException;
import com.archivage.document.dto.DocumentDto;
import com.archivage.document.dto.DocumentMetadataPatchRequest;
import com.archivage.document.dto.DocumentMetadataUpdateRequest;
import com.archivage.document.dto.DocumentStatusUpdateRequest;
import com.archivage.document.dto.MetadataSuggestionsDto;
import com.archivage.document.entity.Document;
import com.archivage.document.mapper.DocumentMapper;
import com.archivage.document.policy.DocumentAccessService;
import com.archivage.document.repository.DocumentRepository;
import com.archivage.metadata.entity.DocumentTypeEntity;
import com.archivage.metadata.repository.DocumentTypeRepository;
import com.archivage.metadata.validation.CustomMetadataValidator;
import com.archivage.ocr.OcrJobService;
import com.archivage.user.entity.User;
import com.archivage.user.repository.DepartmentRepository;
import com.archivage.user.entity.Department;
import com.archivage.search.dto.SearchRequest;
import com.archivage.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DocumentServiceTest {

    @Mock
    private DocumentRepository documentRepository;
    @Mock
    private DocumentTypeRepository documentTypeRepository;
    @Mock
    private DocumentMapper documentMapper;
    @Mock
    private AuditService auditService;
    @Mock
    private OcrJobService ocrJobService;
    @Mock
    private DepartmentRepository departmentRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private AuditLogRepository auditLogRepository;
    @Mock
    private CustomMetadataValidator customMetadataValidator;
    @Mock
    private MetadataSuggestionService metadataSuggestionService;

    private DocumentService documentService;

    @BeforeEach
    void setUp() {
        documentService = new DocumentService(
                documentRepository,
                documentTypeRepository,
                documentMapper,
                auditService,
                ocrJobService,
                departmentRepository,
                userRepository,
                new DocumentAccessService(),
                auditLogRepository,
                customMetadataValidator,
                metadataSuggestionService
        );
    }

    @Test
    void listDeletedForAdmin_empty() {
        when(documentRepository.findDeletedPage(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        var page = documentService.listDeletedForAdmin(0, 20);

        assertThat(page.content()).isEmpty();
    }

    @Test
    void getById_admin_readsAndAudits() {
        User admin = User.builder()
                .username("admin")
                .passwordHash("x")
                .role(Role.ADMIN)
                .active(true)
                .build();
        admin.setId(1L);
        UserPrincipal principal = new UserPrincipal(admin);

        DocumentTypeEntity type = DocumentTypeEntity.builder()
                .code("T")
                .labelFr("L")
                .labelPt("L")
                .active(true)
                .build();
        type.setId(1L);

        Document doc = Document.builder()
                .title("Doc")
                .documentType(type)
                .folderNumber("F")
                .documentDate(LocalDate.now())
                .status(DocumentStatus.VALIDATED)
                .language(DocumentLanguage.FRENCH)
                .confidentialityLevel(ConfidentialityLevel.INTERNAL)
                .build();
        doc.setId(9L);
        doc.setUuid(UUID.randomUUID());

        DocumentDto dto = new DocumentDto(
                9L,
                doc.getUuid(),
                1L,
                null,
                "Doc",
                "T",
                "L",
                "L",
                "F",
                LocalDate.now(),
                null,
                DocumentStatus.VALIDATED,
                DocumentLanguage.FRENCH,
                ConfidentialityLevel.INTERNAL,
                null,
                false,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );

        when(userRepository.findWithDepartmentById(1L)).thenReturn(Optional.of(admin));
        when(documentRepository.findDetailById(9L)).thenReturn(Optional.of(doc));
        when(documentMapper.toDto(doc)).thenReturn(dto);

        DocumentDto out = documentService.getById(9L, principal);

        assertThat(out.id()).isEqualTo(9L);
        verify(auditService).log("DOCUMENT_VIEW", admin, "DOCUMENT", 9L, Map.of());
    }

    @Test
    void getById_documentNotFound_throwsNotFound() {
        User admin = User.builder()
                .username("admin")
                .passwordHash("x")
                .role(Role.ADMIN)
                .active(true)
                .build();
        admin.setId(1L);
        UserPrincipal principal = new UserPrincipal(admin);

        when(userRepository.findWithDepartmentById(1L)).thenReturn(Optional.of(admin));
        when(documentRepository.findDetailById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> documentService.getById(404L, principal))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException e = (ApiException) ex;
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(e.getCode()).isEqualTo("NOT_FOUND");
                });
    }

    @Test
    void getById_readerNotInDatabase_throwsUnauthorized() {
        UserPrincipal principal = new UserPrincipal(
                User.builder().username("ghost").passwordHash("x").role(Role.LECTEUR).active(true).build()
        );
        principal.getUser().setId(999L);

        when(userRepository.findWithDepartmentById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> documentService.getById(1L, principal))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException e = (ApiException) ex;
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
                    assertThat(e.getCode()).isEqualTo("UNAUTHORIZED");
                });
    }

    @Test
    void getById_lecteur_secretDocument_forbidden() {
        Department dept = Department.builder().code("D1").nameFr("A").namePt("A").build();
        dept.setId(1L);

        User lecteur = User.builder()
                .username("lec")
                .passwordHash("x")
                .role(Role.LECTEUR)
                .active(true)
                .department(dept)
                .build();
        lecteur.setId(2L);
        UserPrincipal principal = new UserPrincipal(lecteur);

        DocumentTypeEntity type = DocumentTypeEntity.builder()
                .code("T")
                .labelFr("L")
                .labelPt("L")
                .active(true)
                .build();
        type.setId(1L);

        Document doc = Document.builder()
                .title("Secret")
                .documentType(type)
                .folderNumber("F")
                .documentDate(LocalDate.now())
                .status(DocumentStatus.VALIDATED)
                .language(DocumentLanguage.FRENCH)
                .confidentialityLevel(ConfidentialityLevel.SECRET)
                .build();
        doc.setId(50L);
        doc.setDepartment(dept);

        when(userRepository.findWithDepartmentById(2L)).thenReturn(Optional.of(lecteur));
        when(documentRepository.findDetailById(50L)).thenReturn(Optional.of(doc));

        assertThatThrownBy(() -> documentService.getById(50L, principal))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException e = (ApiException) ex;
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(e.getCode()).isEqualTo("ACCESS_DENIED");
                });
    }

    @Test
    void downloadOcr_whenNoOcrFile_throwsNotFound() {
        User reader = User.builder()
                .username("r")
                .passwordHash("x")
                .role(Role.ADMIN)
                .active(true)
                .build();
        reader.setId(1L);
        UserPrincipal principal = new UserPrincipal(reader);

        DocumentTypeEntity type = DocumentTypeEntity.builder()
                .code("T")
                .labelFr("L")
                .labelPt("L")
                .active(true)
                .build();
        type.setId(1L);

        Document doc = Document.builder()
                .title("Doc")
                .documentType(type)
                .folderNumber("F")
                .documentDate(LocalDate.now())
                .status(DocumentStatus.VALIDATED)
                .language(DocumentLanguage.FRENCH)
                .confidentialityLevel(ConfidentialityLevel.INTERNAL)
                .build();
        doc.setId(3L);
        doc.setOcrPath(null);

        when(userRepository.findWithDepartmentById(1L)).thenReturn(Optional.of(reader));
        when(documentRepository.findDetailById(3L)).thenReturn(Optional.of(doc));

        assertThatThrownBy(() -> documentService.downloadOcr(3L, principal, false))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException e = (ApiException) ex;
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(e.getCode()).isEqualTo("NO_OCR");
                });
    }

    @Test
    void updateStatus_documentNotFound_throwsNotFound() {
        User admin = User.builder()
                .username("admin")
                .passwordHash("x")
                .role(Role.ADMIN)
                .active(true)
                .build();
        admin.setId(1L);
        UserPrincipal principal = new UserPrincipal(admin);

        when(userRepository.findWithDepartmentById(1L)).thenReturn(Optional.of(admin));
        when(documentRepository.findDetailById(77L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> documentService.updateStatus(
                77L,
                new DocumentStatusUpdateRequest(DocumentStatus.ARCHIVED),
                principal
        ))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException e = (ApiException) ex;
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(e.getCode()).isEqualTo("NOT_FOUND");
                });
    }

    @Test
    void getByUuid_loadsDetailAndAudits() {
        User admin = admin();
        UserPrincipal principal = new UserPrincipal(admin);
        UUID uuid = UUID.randomUUID();
        DocumentTypeEntity type = docType(1L);
        Document minimal = Document.builder()
                .title("x")
                .documentType(type)
                .folderNumber("F")
                .documentDate(LocalDate.now())
                .status(DocumentStatus.VALIDATED)
                .language(DocumentLanguage.FRENCH)
                .confidentialityLevel(ConfidentialityLevel.INTERNAL)
                .build();
        minimal.setId(12L);
        minimal.setUuid(uuid);
        Document detail = Document.builder()
                .title("Full")
                .documentType(type)
                .folderNumber("F")
                .documentDate(LocalDate.now())
                .status(DocumentStatus.VALIDATED)
                .language(DocumentLanguage.FRENCH)
                .confidentialityLevel(ConfidentialityLevel.INTERNAL)
                .build();
        detail.setId(12L);
        detail.setUuid(uuid);
        DocumentDto dto = minimalDto(detail);

        when(userRepository.findWithDepartmentById(1L)).thenReturn(Optional.of(admin));
        when(documentRepository.findByUuid(uuid)).thenReturn(Optional.of(minimal));
        when(documentRepository.findDetailById(12L)).thenReturn(Optional.of(detail));
        when(documentMapper.toDto(detail)).thenReturn(dto);

        assertThat(documentService.getByUuid(uuid, principal).id()).isEqualTo(12L);
        verify(auditService).log("DOCUMENT_VIEW", admin, "DOCUMENT", 12L, Map.of());
    }

    @Test
    void getMetadataSuggestions_delegatesToSuggestionService() {
        User admin = admin();
        UserPrincipal principal = new UserPrincipal(admin);
        DocumentTypeEntity type = docType(1L);
        Document doc = baseDocument(3L, type);
        doc.setOcrText("Réf: X 2024-01-01");

        when(userRepository.findWithDepartmentById(1L)).thenReturn(Optional.of(admin));
        when(documentRepository.findDetailById(3L)).thenReturn(Optional.of(doc));
        when(metadataSuggestionService.suggestFromOcrText("Réf: X 2024-01-01"))
                .thenReturn(new MetadataSuggestionsDto(List.of(), List.of(), List.of()));

        MetadataSuggestionsDto out = documentService.getMetadataSuggestions(3L, principal);
        assertThat(out.isoDates()).isEmpty();
    }

    @ParameterizedTest
    @EnumSource(SearchRequest.SearchSort.class)
    void list_appliesEachSortVariant(SearchRequest.SearchSort sort) {
        User reader = admin();
        UserPrincipal principal = new UserPrincipal(reader);
        DocumentTypeEntity type = docType(1L);
        Document row = baseDocument(1L, type);
        Document detail = baseDocument(1L, type);
        DocumentDto dto = minimalDto(detail);

        SearchRequest filters = new SearchRequest(
                null, null, null, null, null, null, null, null, null,
                sort, 0, 20
        );

        when(userRepository.findWithDepartmentById(1L)).thenReturn(Optional.of(reader));
        when(documentRepository.searchDocuments(eq(filters), any(Pageable.class), eq(reader)))
                .thenReturn(new PageImpl<>(List.of(row), PageRequest.of(0, 20), 1));
        when(documentRepository.findDetailById(1L)).thenReturn(Optional.of(detail));
        when(documentMapper.toDto(detail)).thenReturn(dto);

        assertThat(documentService.list(filters, principal).content()).hasSize(1);
    }

    @Test
    void updateMetadata_sameType_savesAndAudits() {
        User admin = admin();
        UserPrincipal principal = new UserPrincipal(admin);
        DocumentTypeEntity type = docType(1L);
        Document doc = baseDocument(8L, type);
        DocumentMetadataUpdateRequest req = new DocumentMetadataUpdateRequest(
                "Nouveau titre",
                1L,
                "F-99",
                LocalDate.now(),
                DocumentLanguage.FRENCH,
                ConfidentialityLevel.INTERNAL,
                null,
                null,
                null,
                null,
                List.of("t1"),
                null
        );
        DocumentDto dto = minimalDto(doc);

        when(userRepository.findWithDepartmentById(1L)).thenReturn(Optional.of(admin));
        when(documentRepository.findDetailById(8L)).thenReturn(Optional.of(doc));
        when(documentTypeRepository.findById(1L)).thenReturn(Optional.of(type));
        when(documentRepository.save(any(Document.class))).thenAnswer(inv -> inv.getArgument(0));
        when(documentMapper.toDto(any(Document.class))).thenReturn(dto);

        documentService.updateMetadata(8L, req, principal);

        verify(auditService).log("DOCUMENT_UPDATE", admin, "DOCUMENT", 8L, Map.of("fields", "metadata"));
    }

    @Test
    void patchMetadata_blankTitle_throwsBadRequest() {
        User admin = admin();
        UserPrincipal principal = new UserPrincipal(admin);
        DocumentTypeEntity type = docType(1L);
        Document doc = baseDocument(8L, type);

        when(userRepository.findWithDepartmentById(1L)).thenReturn(Optional.of(admin));
        when(documentRepository.findDetailById(8L)).thenReturn(Optional.of(doc));

        assertThatThrownBy(() -> documentService.patchMetadata(
                8L,
                new DocumentMetadataPatchRequest("   ", null, null, null, null, null, null, null, null, null, null, null),
                principal
        ))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException e = (ApiException) ex;
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(e.getCode()).isEqualTo("INVALID_TITLE");
                });
    }

    @Test
    void updateStatus_success() {
        User admin = admin();
        UserPrincipal principal = new UserPrincipal(admin);
        DocumentTypeEntity type = docType(1L);
        Document doc = baseDocument(8L, type);
        DocumentDto dto = minimalDto(doc);

        when(userRepository.findWithDepartmentById(1L)).thenReturn(Optional.of(admin));
        when(documentRepository.findDetailById(8L)).thenReturn(Optional.of(doc));
        when(documentRepository.save(any(Document.class))).thenAnswer(inv -> inv.getArgument(0));
        when(documentMapper.toDto(any(Document.class))).thenReturn(dto);

        documentService.updateStatus(8L, new DocumentStatusUpdateRequest(DocumentStatus.ARCHIVED), principal);

        verify(auditService).log("DOCUMENT_STATUS", admin, "DOCUMENT", 8L, Map.of("status", "ARCHIVED"));
    }

    @Test
    void downloadOriginal_withAudit_logsDownload() {
        User admin = admin();
        UserPrincipal principal = new UserPrincipal(admin);
        DocumentTypeEntity type = docType(1L);
        Document doc = baseDocument(8L, type);
        doc.setOriginalPath("C:\\tmp\\f.pdf");

        when(userRepository.findWithDepartmentById(1L)).thenReturn(Optional.of(admin));
        when(documentRepository.findDetailById(8L)).thenReturn(Optional.of(doc));

        Resource r = documentService.downloadOriginal(8L, principal, true);
        assertThat(r).isNotNull();
        verify(auditService).log("DOCUMENT_DOWNLOAD_ORIGINAL", admin, "DOCUMENT", 8L, Map.of());
    }

    @Test
    void downloadOcr_withPath_logsWhenAudit() {
        User admin = admin();
        UserPrincipal principal = new UserPrincipal(admin);
        DocumentTypeEntity type = docType(1L);
        Document doc = baseDocument(8L, type);
        doc.setOcrPath("C:\\tmp\\ocr.pdf");

        when(userRepository.findWithDepartmentById(1L)).thenReturn(Optional.of(admin));
        when(documentRepository.findDetailById(8L)).thenReturn(Optional.of(doc));

        Resource r = documentService.downloadOcr(8L, principal, true);
        assertThat(r).isNotNull();
        verify(auditService).log("DOCUMENT_DOWNLOAD_OCR", admin, "DOCUMENT", 8L, Map.of());
    }

    @Test
    void reprocessOcr_enqueuesJob() {
        User admin = admin();
        UserPrincipal principal = new UserPrincipal(admin);
        DocumentTypeEntity type = docType(1L);
        Document doc = baseDocument(8L, type);

        when(userRepository.findWithDepartmentById(1L)).thenReturn(Optional.of(admin));
        when(documentRepository.findDetailById(8L)).thenReturn(Optional.of(doc));
        when(documentRepository.save(any(Document.class))).thenAnswer(inv -> inv.getArgument(0));

        documentService.reprocessOcr(8L, principal);

        verify(ocrJobService).enqueue(8L);
        verify(auditService).log("DOCUMENT_REPROCESS_OCR", admin, "DOCUMENT", 8L, Map.of());
    }

    @Test
    void softDelete_marksDeleted() {
        User admin = admin();
        UserPrincipal principal = new UserPrincipal(admin);
        DocumentTypeEntity type = docType(1L);
        Document doc = baseDocument(8L, type);

        when(userRepository.findWithDepartmentById(1L)).thenReturn(Optional.of(admin));
        when(documentRepository.findDetailById(8L)).thenReturn(Optional.of(doc));
        when(documentRepository.save(any(Document.class))).thenAnswer(inv -> inv.getArgument(0));

        documentService.softDelete(8L, principal);

        assertThat(doc.isDeleted()).isTrue();
        assertThat(doc.getDeletedAt()).isNotNull();
        verify(auditService).log("DOCUMENT_SOFT_DELETE", admin, "DOCUMENT", 8L, Map.of());
    }

    @Test
    void restoreDeleted_clearsFlags() {
        User admin = admin();
        UserPrincipal principal = new UserPrincipal(admin);
        DocumentTypeEntity type = docType(1L);
        Document doc = baseDocument(8L, type);
        doc.setDeleted(true);
        doc.setDeletedAt(Instant.now());

        when(userRepository.findWithDepartmentById(1L)).thenReturn(Optional.of(admin));
        when(documentRepository.findDeletedById(8L)).thenReturn(Optional.of(doc));
        when(documentRepository.save(any(Document.class))).thenAnswer(inv -> inv.getArgument(0));

        documentService.restoreDeleted(8L, principal);

        assertThat(doc.isDeleted()).isFalse();
        assertThat(doc.getDeletedAt()).isNull();
        verify(auditService).log("DOCUMENT_RESTORE", admin, "DOCUMENT", 8L, Map.of());
    }

    @Test
    void documentHistory_returnsPage() {
        User admin = admin();
        UserPrincipal principal = new UserPrincipal(admin);
        DocumentTypeEntity type = docType(1L);
        Document doc = baseDocument(8L, type);

        when(userRepository.findWithDepartmentById(1L)).thenReturn(Optional.of(admin));
        when(documentRepository.findDetailById(8L)).thenReturn(Optional.of(doc));
        when(auditLogRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 10), 0));

        assertThat(documentService.documentHistory(8L, 0, 10, false, principal).content()).isEmpty();
    }

    private static User admin() {
        User u = User.builder()
                .username("admin")
                .passwordHash("x")
                .role(Role.ADMIN)
                .active(true)
                .build();
        u.setId(1L);
        return u;
    }

    private static DocumentTypeEntity docType(long id) {
        DocumentTypeEntity t = DocumentTypeEntity.builder()
                .code("T")
                .labelFr("L")
                .labelPt("L")
                .active(true)
                .build();
        t.setId(id);
        return t;
    }

    private static Document baseDocument(long id, DocumentTypeEntity type) {
        Document d = Document.builder()
                .title("Doc")
                .documentType(type)
                .folderNumber("F")
                .documentDate(LocalDate.now())
                .status(DocumentStatus.VALIDATED)
                .language(DocumentLanguage.FRENCH)
                .confidentialityLevel(ConfidentialityLevel.INTERNAL)
                .build();
        d.setId(id);
        d.setUuid(UUID.randomUUID());
        return d;
    }

    private static DocumentDto minimalDto(Document doc) {
        return new DocumentDto(
                doc.getId(),
                doc.getUuid(),
                doc.getDocumentType().getId(),
                null,
                doc.getTitle(),
                doc.getDocumentType().getCode(),
                "L",
                "L",
                doc.getFolderNumber(),
                doc.getDocumentDate(),
                null,
                doc.getStatus(),
                doc.getLanguage(),
                doc.getConfidentialityLevel(),
                null,
                false,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }
}
