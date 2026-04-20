package com.archivage.admin;

import com.archivage.admin.dto.CreateDocumentTypeRequest;
import com.archivage.admin.dto.CreateUserRequest;
import com.archivage.admin.dto.UpdateUserRequest;
import com.archivage.audit.AuditService;
import com.archivage.audit.entity.AuditLog;
import com.archivage.audit.repository.AuditLogRepository;
import com.archivage.common.domain.DocumentStatus;
import com.archivage.common.domain.DocumentLanguage;
import com.archivage.common.domain.OcrJobStatus;
import com.archivage.common.domain.Role;
import com.archivage.common.domain.ConfidentialityLevel;
import com.archivage.common.exception.ApiException;
import com.archivage.document.entity.Document;
import com.archivage.document.repository.DocumentRepository;
import com.archivage.metadata.entity.DocumentTypeEntity;
import com.archivage.ocr.entity.OcrJob;
import com.archivage.metadata.repository.DocumentTypeRepository;
import com.archivage.ocr.repository.OcrJobRepository;
import com.archivage.user.entity.Department;
import com.archivage.user.entity.User;
import com.archivage.user.repository.DepartmentRepository;
import com.archivage.user.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private DepartmentRepository departmentRepository;
    @Mock
    private DocumentTypeRepository documentTypeRepository;
    @Mock
    private DocumentRepository documentRepository;
    @Mock
    private OcrJobRepository ocrJobRepository;
    @Mock
    private AuditLogRepository auditLogRepository;
    @Mock
    private AuditService auditService;
    @Mock
    private PasswordEncoder passwordEncoder;

    private AdminService adminService;

    @BeforeEach
    void setUp() {
        adminService = new AdminService(
                userRepository,
                departmentRepository,
                documentTypeRepository,
                documentRepository,
                ocrJobRepository,
                auditLogRepository,
                auditService,
                passwordEncoder
        );
    }

    @Test
    void createUser_usernameTaken_throwsConflict() {
        User existing = User.builder()
                .username("dup")
                .passwordHash("x")
                .role(Role.LECTEUR)
                .active(true)
                .build();
        when(userRepository.findByUsername("dup")).thenReturn(Optional.of(existing));

        User actor = User.builder()
                .username("admin")
                .passwordHash("x")
                .role(Role.ADMIN)
                .active(true)
                .build();
        actor.setId(1L);

        CreateUserRequest req = new CreateUserRequest(
                "dup",
                "password12!",
                "n@example.com",
                "Name",
                Role.LECTEUR,
                null,
                true
        );

        assertThatThrownBy(() -> adminService.createUser(req, actor))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException e = (ApiException) ex;
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(e.getCode()).isEqualTo("USERNAME_TAKEN");
                });
    }

    @Test
    void createDocumentType_codeTaken_throwsConflict() {
        DocumentTypeEntity existing = DocumentTypeEntity.builder()
                .code("INV")
                .labelFr("x")
                .labelPt("y")
                .active(true)
                .build();
        when(documentTypeRepository.findByCode("INV")).thenReturn(Optional.of(existing));

        User actor = User.builder()
                .username("admin")
                .passwordHash("x")
                .role(Role.ADMIN)
                .active(true)
                .build();
        actor.setId(1L);

        CreateDocumentTypeRequest req = new CreateDocumentTypeRequest(
                "INV", "Libellé", "Etiqueta", true, null
        );

        assertThatThrownBy(() -> adminService.createDocumentType(req, actor))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException e = (ApiException) ex;
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(e.getCode()).isEqualTo("CODE_TAKEN");
                });
    }

    @Test
    void cancelPendingOcrJob_whenNotPending_throwsConflict() {
        DocumentTypeEntity type = DocumentTypeEntity.builder()
                .code("T")
                .labelFr("L")
                .labelPt("L")
                .active(true)
                .build();
        type.setId(1L);

        Document doc = Document.builder()
                .title("d")
                .documentType(type)
                .folderNumber("F")
                .documentDate(LocalDate.now())
                .status(DocumentStatus.VALIDATED)
                .language(DocumentLanguage.FRENCH)
                .confidentialityLevel(ConfidentialityLevel.INTERNAL)
                .build();
        doc.setId(10L);

        OcrJob job = OcrJob.builder()
                .document(doc)
                .status(OcrJobStatus.PROCESSING)
                .retryCount(0)
                .ocrEngine("tesseract")
                .ocrLang("fra")
                .build();
        job.setId(1L);

        when(ocrJobRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(job));

        assertThatThrownBy(() -> adminService.cancelPendingOcrJob(1L))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException e = (ApiException) ex;
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(e.getCode()).isEqualTo("OCR_JOB_NOT_CANCELLABLE");
                });
    }

    @Test
    void listUsers_returnsMappedPage() {
        User u = User.builder()
                .username("u1")
                .passwordHash("x")
                .role(Role.LECTEUR)
                .active(true)
                .build();
        u.setId(1L);
        u.setUuid(UUID.randomUUID());
        when(userRepository.findAll(PageRequest.of(0, 20)))
                .thenReturn(new PageImpl<>(List.of(u), PageRequest.of(0, 20), 1));

        var page = adminService.listUsers(0, 20);

        assertThat(page.content()).hasSize(1);
        assertThat(page.content().getFirst().username()).isEqualTo("u1");
    }

    @Test
    void createUser_success_persistsAndAudits() {
        when(userRepository.findByUsername("newuser")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("secret123!")).thenReturn("hash");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User saved = inv.getArgument(0);
            saved.setId(9L);
            saved.setUuid(UUID.randomUUID());
            return saved;
        });

        User actor = User.builder().username("admin").passwordHash("x").role(Role.ADMIN).active(true).build();
        actor.setId(1L);

        CreateUserRequest req = new CreateUserRequest(
                "newuser", "secret123!", "e@x.com", "Full", Role.ARCHIVISTE, null, true);

        var dto = adminService.createUser(req, actor);

        assertThat(dto.username()).isEqualTo("newuser");
        verify(auditService).log(eq("ADMIN_USER_CREATE"), eq(actor), eq("USER"), eq(9L), any());
    }

    @Test
    void createUser_withDepartment_resolvesReference() {
        Department dept = Department.builder().code("D1").nameFr("A").namePt("B").build();
        dept.setId(5L);
        when(userRepository.findByUsername("u")).thenReturn(Optional.empty());
        when(departmentRepository.getReferenceById(5L)).thenReturn(dept);
        when(passwordEncoder.encode("secret123!")).thenReturn("hash");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User saved = inv.getArgument(0);
            saved.setId(2L);
            saved.setUuid(UUID.randomUUID());
            return saved;
        });

        User actor = User.builder().username("admin").passwordHash("x").role(Role.ADMIN).active(true).build();
        actor.setId(1L);
        CreateUserRequest req = new CreateUserRequest(
                "u", "secret123!", null, null, Role.LECTEUR, 5L, true);

        adminService.createUser(req, actor);

        verify(departmentRepository).getReferenceById(5L);
    }

    @Test
    void updateUser_success_updatesFieldsAndPassword() {
        User existing = User.builder()
                .username("old")
                .passwordHash("oldhash")
                .email("a@b.com")
                .role(Role.LECTEUR)
                .active(true)
                .build();
        existing.setId(3L);
        when(userRepository.findById(3L)).thenReturn(Optional.of(existing));
        when(passwordEncoder.encode("newpass12!")).thenReturn("newhash");
        when(userRepository.save(existing)).thenReturn(existing);

        User actor = User.builder().username("admin").passwordHash("x").role(Role.ADMIN).active(true).build();
        actor.setId(1L);

        UpdateUserRequest req = new UpdateUserRequest(
                "x@y.com", "Nom", Role.ADMIN, null, true, "newpass12!");

        var dto = adminService.updateUser(3L, req, actor);

        assertThat(dto.email()).isEqualTo("x@y.com");
        assertThat(dto.fullName()).isEqualTo("Nom");
        assertThat(dto.role()).isEqualTo(Role.ADMIN);
        assertThat(existing.getPasswordHash()).isEqualTo("newhash");
        verify(auditService).log(eq("ADMIN_USER_UPDATE"), eq(actor), eq("USER"), eq(3L), any());
    }

    @Test
    void updateUser_notFound_throws() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());
        User actor = User.builder().username("admin").passwordHash("x").role(Role.ADMIN).active(true).build();
        actor.setId(1L);

        assertThatThrownBy(() -> adminService.updateUser(99L,
                new UpdateUserRequest(null, null, null, null, null, null), actor))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void listDocumentTypes_mapsFields() throws Exception {
        DocumentTypeEntity dt = DocumentTypeEntity.builder()
                .code("C")
                .labelFr("LF")
                .labelPt("LP")
                .active(true)
                .customFieldsSchema(new ObjectMapper().readTree("{\"a\":1}"))
                .build();
        dt.setId(7L);
        when(documentTypeRepository.findAll(PageRequest.of(0, 5)))
                .thenReturn(new PageImpl<>(List.of(dt), PageRequest.of(0, 5), 1));

        var page = adminService.listDocumentTypes(0, 5);

        assertThat(page.content()).hasSize(1);
        assertThat(page.content().getFirst().code()).isEqualTo("C");
        assertThat(page.content().getFirst().customFieldsSchema().toString()).contains("a");
    }

    @Test
    void createDocumentType_success() throws Exception {
        when(documentTypeRepository.findByCode("NEW")).thenReturn(Optional.empty());
        when(documentTypeRepository.save(any(DocumentTypeEntity.class))).thenAnswer(inv -> {
            DocumentTypeEntity e = inv.getArgument(0);
            e.setId(11L);
            return e;
        });

        User actor = User.builder().username("admin").passwordHash("x").role(Role.ADMIN).active(true).build();
        actor.setId(1L);

        var dto = adminService.createDocumentType(
                new CreateDocumentTypeRequest("NEW", "Fr", "Pt", true, new ObjectMapper().readTree("{}")), actor);

        assertThat(dto.id()).isEqualTo(11L);
        verify(auditService).log(eq("ADMIN_DOC_TYPE_CREATE"), eq(actor), eq("DOCUMENT_TYPE"), eq(11L), any());
    }

    @Test
    void dashboard_aggregatesCounts() {
        when(documentRepository.countActiveDocuments()).thenReturn(42L);
        when(documentRepository.count(any(Specification.class))).thenReturn(3L);
        when(ocrJobRepository.countByStatus(any(OcrJobStatus.class))).thenReturn(1L);

        var dash = adminService.dashboard();

        assertThat(dash.totalDocuments()).isEqualTo(42L);
        assertThat(dash.documentsByStatus()).hasSize(DocumentStatus.values().length);
        assertThat(dash.ocrQueue().pending()).isEqualTo(1L);
    }

    @Test
    void ocrQueueStats_returnsCounts() {
        when(ocrJobRepository.countByStatus(OcrJobStatus.PENDING)).thenReturn(2L);
        when(ocrJobRepository.countByStatus(OcrJobStatus.PROCESSING)).thenReturn(1L);
        when(ocrJobRepository.countByStatus(OcrJobStatus.OCR_FAILED)).thenReturn(0L);
        when(ocrJobRepository.countByStatus(OcrJobStatus.CANCELLED)).thenReturn(4L);

        var s = adminService.ocrQueueStats();

        assertThat(s.pending()).isEqualTo(2L);
        assertThat(s.processing()).isEqualTo(1L);
        assertThat(s.failed()).isEqualTo(0L);
        assertThat(s.cancelled()).isEqualTo(4L);
    }

    @Test
    void cancelPendingOcrJob_success() {
        DocumentTypeEntity type = DocumentTypeEntity.builder()
                .code("T").labelFr("L").labelPt("L").active(true).build();
        type.setId(1L);
        Document doc = Document.builder()
                .title("d")
                .documentType(type)
                .folderNumber("F")
                .documentDate(LocalDate.now())
                .status(DocumentStatus.VALIDATED)
                .language(DocumentLanguage.FRENCH)
                .confidentialityLevel(ConfidentialityLevel.INTERNAL)
                .build();
        doc.setId(10L);

        OcrJob job = OcrJob.builder()
                .document(doc)
                .status(OcrJobStatus.PENDING)
                .retryCount(0)
                .ocrEngine("t")
                .ocrLang("fra")
                .build();
        job.setId(1L);

        when(ocrJobRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(job));
        when(ocrJobRepository.save(job)).thenReturn(job);

        adminService.cancelPendingOcrJob(1L);

        assertThat(job.getStatus()).isEqualTo(OcrJobStatus.CANCELLED);
        assertThat(job.getCompletedAt()).isNotNull();
    }

    @Test
    void cancelPendingOcrJob_notFound_throws() {
        when(ocrJobRepository.findByIdForUpdate(2L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> adminService.cancelPendingOcrJob(2L))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getCode()).isEqualTo("NOT_FOUND"));
    }

    @Test
    void ocrQueue_mapsJobs() {
        DocumentTypeEntity type = DocumentTypeEntity.builder()
                .code("T").labelFr("L").labelPt("L").active(true).build();
        type.setId(1L);
        Document doc = Document.builder()
                .title("d")
                .documentType(type)
                .folderNumber("F")
                .documentDate(LocalDate.now())
                .status(DocumentStatus.VALIDATED)
                .language(DocumentLanguage.FRENCH)
                .confidentialityLevel(ConfidentialityLevel.INTERNAL)
                .build();
        doc.setId(99L);

        OcrJob job = OcrJob.builder()
                .document(doc)
                .status(OcrJobStatus.OCR_FAILED)
                .retryCount(2)
                .ocrEngine("t")
                .ocrLang("fra")
                .errorMessage("err")
                .build();
        job.setId(5L);
        job.setStartedAt(Instant.parse("2024-01-01T00:00:00Z"));
        job.setCompletedAt(Instant.parse("2024-01-01T00:01:00Z"));

        when(ocrJobRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(0, 10)))
                .thenReturn(new PageImpl<>(List.of(job), PageRequest.of(0, 10), 1));

        var page = adminService.ocrQueue(0, 10);

        assertThat(page.content()).hasSize(1);
        assertThat(page.content().getFirst().documentId()).isEqualTo(99L);
        assertThat(page.content().getFirst().errorMessage()).isEqualTo("err");
    }

    @Test
    void auditLogs_swapsDatesWhenFromAfterTo() {
        when(auditLogRepository.findAll(any(Specification.class), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 10), 0));

        adminService.auditLogs(0, 10,
                LocalDate.of(2024, 6, 10),
                LocalDate.of(2024, 1, 1),
                null, null, null, null);

        verify(auditLogRepository).findAll(any(Specification.class), any(PageRequest.class));
    }

    @Test
    void auditLogs_mapsRows_withUserAndNullDetails() {
        User u = User.builder().username("actor").passwordHash("x").role(Role.ADMIN).active(true).build();
        u.setId(8L);
        AuditLog log = AuditLog.builder()
                .user(u)
                .action("DOCUMENT_VIEW")
                .resourceType("DOCUMENT")
                .resourceId(1L)
                .details(null)
                .ipAddress("::1")
                .createdAt(Instant.parse("2024-05-01T12:00:00Z"))
                .build();
        log.setId(100L);

        when(auditLogRepository.findAll(any(Specification.class), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(log), PageRequest.of(0, 10), 1));

        var page = adminService.auditLogs(0, 10, null, null, null, null, null, "Europe/Paris");

        assertThat(page.content()).hasSize(1);
        assertThat(page.content().getFirst().username()).isEqualTo("actor");
        assertThat(page.content().getFirst().details()).isEmpty();
    }

    @Test
    void auditLogs_invalidTimeZone_fallsBackToUtc() {
        when(auditLogRepository.findAll(any(Specification.class), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 5), 0));

        adminService.auditLogs(0, 5, null, null, null, null, null, "not a valid zone id !!!");

        verify(auditLogRepository).findAll(any(Specification.class), any(PageRequest.class));
    }
}
