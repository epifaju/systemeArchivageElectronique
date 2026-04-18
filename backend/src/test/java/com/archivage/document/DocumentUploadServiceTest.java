package com.archivage.document;

import com.archivage.audit.AuditService;
import com.archivage.auth.security.UserPrincipal;
import com.archivage.common.domain.Role;
import com.archivage.common.exception.DuplicateDocumentException;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
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
}
