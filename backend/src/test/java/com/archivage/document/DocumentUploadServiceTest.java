package com.archivage.document;

import com.archivage.audit.AuditService;
import com.archivage.auth.security.UserPrincipal;
import com.archivage.common.domain.ConfidentialityLevel;
import com.archivage.common.domain.DocumentLanguage;
import com.archivage.common.domain.DocumentStatus;
import com.archivage.common.domain.Role;
import com.archivage.common.exception.ApiException;
import com.archivage.common.exception.DuplicateDocumentException;
import com.archivage.document.dto.DocumentDto;
import com.archivage.document.dto.UploadRequest;
import com.archivage.document.entity.Document;
import com.archivage.document.mapper.DocumentMapper;
import com.archivage.document.repository.DocumentRepository;
import com.archivage.metadata.entity.DocumentTypeEntity;
import com.archivage.metadata.repository.DocumentTypeRepository;
import com.archivage.metadata.validation.CustomMetadataValidator;
import com.archivage.ocr.OcrJobService;
import com.archivage.storage.FileStorageService;
import com.archivage.storage.clamav.ClamAvScanner;
import com.archivage.user.entity.User;
import com.archivage.user.repository.DepartmentRepository;
import com.archivage.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DocumentUploadServiceTest {

    @Mock
    private DocumentRepository documentRepository;
    @Mock
    private DocumentTypeRepository documentTypeRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private FileStorageService fileStorageService;
    @Mock
    private DocumentMapper documentMapper;
    @Mock
    private OcrJobService ocrJobService;
    @Mock
    private AuditService auditService;
    @Mock
    private DepartmentRepository departmentRepository;
    @Mock
    private ClamAvScanner clamAvScanner;
    @Mock
    private CustomMetadataValidator customMetadataValidator;

    private DocumentUploadService service;

    @BeforeEach
    void setUp() {
        service = new DocumentUploadService(
                documentRepository,
                documentTypeRepository,
                userRepository,
                fileStorageService,
                documentMapper,
                ocrJobService,
                auditService,
                departmentRepository,
                clamAvScanner,
                customMetadataValidator
        );
        org.mockito.Mockito.when(clamAvScanner.isEnabled()).thenReturn(false);
    }

    @Test
    void upload_rejectsDuplicateSha256() throws Exception {
        MultipartFile file = new MockMultipartFile("f", "a.pdf", "application/pdf", "%PDF-1.4 test".getBytes());
        when(fileStorageService.detectMimeType(any(byte[].class))).thenReturn("application/pdf");
        when(fileStorageService.computeSha256(any(byte[].class))).thenReturn("deadbeef");
        when(documentRepository.findBySha256("deadbeef")).thenReturn(Optional.of(new com.archivage.document.entity.Document()));

        User u = User.builder().username("x").passwordHash("x").role(Role.AGENT).active(true).build();
        u.setId(1L);
        UserPrincipal principal = new UserPrincipal(u);

        var req = new com.archivage.document.dto.UploadRequest(
                "titre", 1L, "F-1", java.time.LocalDate.now(),
                com.archivage.common.domain.DocumentLanguage.FRENCH,
                com.archivage.common.domain.ConfidentialityLevel.INTERNAL,
                null, null, null, null, null,
                null
        );

        assertThatThrownBy(() -> service.upload(file, req, principal))
                .isInstanceOf(DuplicateDocumentException.class);

        verify(ocrJobService, never()).enqueue(anyLong());
    }

    @Test
    void upload_rejectsEmptyFile() {
        User u = User.builder().username("x").passwordHash("x").role(Role.AGENT).active(true).build();
        u.setId(1L);
        UserPrincipal principal = new UserPrincipal(u);
        MultipartFile empty = new MockMultipartFile("f", "a.pdf", "application/pdf", new byte[0]);
        var req = new UploadRequest(
                "titre", 1L, "F-1", LocalDate.now(),
                DocumentLanguage.FRENCH,
                ConfidentialityLevel.INTERNAL,
                null, null, null, null, null,
                null
        );
        assertThatThrownBy(() -> service.upload(empty, req, principal))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void ingestDocument_rejectsUnsupportedMime() {
        User u = User.builder().username("x").passwordHash("x").role(Role.AGENT).active(true).build();
        u.setId(1L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(u));
        when(fileStorageService.detectMimeType(any(byte[].class))).thenReturn("application/zip");
        byte[] content = "x".getBytes();
        var req = new UploadRequest(
                "titre", 1L, "F-1", LocalDate.now(),
                DocumentLanguage.FRENCH,
                ConfidentialityLevel.INTERNAL,
                null, null, null, null, null,
                null
        );
        assertThatThrownBy(() -> service.ingestDocument(content, "f.bin", req, u))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void ingestDocument_rejectsInactiveType() throws Exception {
        User u = User.builder().username("x").passwordHash("x").role(Role.AGENT).active(true).build();
        u.setId(1L);
        DocumentTypeEntity type = DocumentTypeEntity.builder()
                .code("T")
                .labelFr("L")
                .labelPt("L")
                .active(false)
                .build();
        type.setId(1L);
        when(fileStorageService.detectMimeType(any(byte[].class))).thenReturn("application/pdf");
        when(fileStorageService.computeSha256(any(byte[].class))).thenReturn("uniquehash1");
        when(documentRepository.findBySha256("uniquehash1")).thenReturn(Optional.empty());
        when(fileStorageService.computeSha256(any(byte[].class))).thenReturn("uniquehash1");
        when(documentTypeRepository.findById(1L)).thenReturn(Optional.of(type));
        byte[] pdf = "%PDF-1.4\n".getBytes();
        var req = new UploadRequest(
                "titre", 1L, "F-1", LocalDate.now(),
                DocumentLanguage.FRENCH,
                ConfidentialityLevel.INTERNAL,
                null, null, null, null, null,
                null
        );
        assertThatThrownBy(() -> service.ingestDocument(pdf, "a.pdf", req, u))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void ingestDocument_success_storesAndEnqueues() throws Exception {
        User u = User.builder().username("x").passwordHash("x").role(Role.AGENT).active(true).build();
        u.setId(1L);
        DocumentTypeEntity type = DocumentTypeEntity.builder()
                .code("T")
                .labelFr("L")
                .labelPt("L")
                .active(true)
                .build();
        type.setId(1L);
        byte[] pdf = "%PDF-1.4\n".getBytes();
        var req = new UploadRequest(
                "titre", 1L, "F-1", LocalDate.now(),
                DocumentLanguage.FRENCH,
                ConfidentialityLevel.INTERNAL,
                null, null, null, null, null,
                null
        );

        when(fileStorageService.detectMimeType(any(byte[].class))).thenReturn("application/pdf");
        when(fileStorageService.computeSha256(any(byte[].class))).thenReturn("uniquehash2");
        when(documentRepository.findBySha256("uniquehash2")).thenReturn(Optional.empty());
        when(documentTypeRepository.findById(1L)).thenReturn(Optional.of(type));
        when(userRepository.findById(1L)).thenReturn(Optional.of(u));
        when(customMetadataValidator.validateAndNormalize(any(), any())).thenReturn(Map.of());
        when(documentRepository.save(any(Document.class))).thenAnswer(inv -> {
            Document d = inv.getArgument(0);
            if (d.getId() == null) {
                d.setId(200L);
            }
            if (d.getUuid() == null) {
                d.setUuid(UUID.randomUUID());
            }
            return d;
        });
        when(fileStorageService.store(any(byte[].class), any(UUID.class), anyString())).thenReturn("/tmp/f.pdf");
        Document detail = Document.builder()
                .title("titre")
                .documentType(type)
                .folderNumber("F-1")
                .documentDate(LocalDate.now())
                .status(DocumentStatus.PENDING)
                .language(DocumentLanguage.FRENCH)
                .confidentialityLevel(ConfidentialityLevel.INTERNAL)
                .build();
        detail.setId(200L);
        detail.setUuid(UUID.randomUUID());
        when(documentRepository.findDetailById(200L)).thenReturn(Optional.of(detail));
        DocumentDto dtoOut = mock(DocumentDto.class);
        when(dtoOut.id()).thenReturn(200L);
        when(documentMapper.toDto(detail)).thenReturn(dtoOut);

        DocumentDto out = service.ingestDocument(pdf, "a.pdf", req, u);

        assertThat(out.id()).isEqualTo(200L);
        verify(ocrJobService).enqueue(200L);
        verify(auditService).log(eq("DOCUMENT_UPLOAD"), eq(u), eq("DOCUMENT"), eq(200L), any());
    }
}
