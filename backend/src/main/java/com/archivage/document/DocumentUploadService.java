package com.archivage.document;

import com.archivage.audit.AuditService;
import com.archivage.auth.security.UserPrincipal;
import com.archivage.common.domain.DocumentStatus;
import com.archivage.common.exception.ApiException;
import com.archivage.common.exception.DuplicateDocumentException;
import com.archivage.document.dto.DocumentDto;
import com.archivage.document.dto.UploadRequest;
import com.archivage.document.entity.Document;
import com.archivage.document.entity.DocumentTag;
import com.archivage.document.mapper.DocumentMapper;
import com.archivage.document.repository.DocumentRepository;
import com.archivage.metadata.entity.DocumentTypeEntity;
import com.archivage.metadata.repository.DocumentTypeRepository;
import com.archivage.ocr.OcrJobService;
import com.archivage.storage.FileStorageService;
import com.archivage.storage.clamav.ClamAvScanner;
import com.archivage.user.entity.Department;
import com.archivage.user.entity.User;
import com.archivage.user.repository.DepartmentRepository;
import com.archivage.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.io.ByteArrayInputStream;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DocumentUploadService {

    private static final List<String> ALLOWED_MIMES = List.of(
            "application/pdf",
            "image/jpeg",
            "image/png",
            "image/tiff",
            "image/tif"
    );

    private final DocumentRepository documentRepository;
    private final DocumentTypeRepository documentTypeRepository;
    private final UserRepository userRepository;
    private final FileStorageService fileStorageService;
    private final DocumentMapper documentMapper;
    private final OcrJobService ocrJobService;
    private final AuditService auditService;
    private final DepartmentRepository departmentRepository;
    private final ClamAvScanner clamAvScanner;

    @Transactional
    public DocumentDto upload(MultipartFile file, UploadRequest request, UserPrincipal principal) {
        if (file == null || file.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FILE_REQUIRED", "Fichier requis");
        }
        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FILE_READ_ERROR", "Lecture du fichier impossible");
        }
        String detected = fileStorageService.detectMimeType(content);
        validateMime(detected);

        if (clamAvScanner.isEnabled()) {
            try {
                clamAvScanner.scanStream(new ByteArrayInputStream(content), content.length);
            } catch (IOException e) {
                throw new ApiException(HttpStatus.BAD_GATEWAY, "ANTIVIRUS_UNAVAILABLE", "Analyse antivirus indisponible: " + e.getMessage());
            }
        }

        String sha256;
        try {
            sha256 = fileStorageService.computeSha256(content);
        } catch (NoSuchAlgorithmException e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "HASH_ERROR", "Calcul d'empreinte impossible");
        }
        documentRepository.findBySha256(sha256).ifPresent(d -> {
            throw new DuplicateDocumentException("Un document avec le même contenu existe déjà");
        });

        DocumentTypeEntity docType = documentTypeRepository.findById(request.documentTypeId())
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "INVALID_TYPE", "Type documentaire inconnu"));
        if (!Boolean.TRUE.equals(docType.getActive())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "TYPE_INACTIVE", "Type documentaire inactif");
        }

        User uploader = userRepository.getReferenceById(principal.getUser().getId());

        Document doc = Document.builder()
                .title(request.title())
                .documentType(docType)
                .folderNumber(request.folderNumber())
                .documentDate(request.documentDate())
                .status(DocumentStatus.PENDING)
                .language(request.language())
                .confidentialityLevel(request.confidentialityLevel())
                .externalReference(request.externalReference())
                .author(request.author())
                .notes(request.notes())
                .uploadedBy(uploader)
                .department(resolveDepartment(request.departmentId(), principal.getUser().getId()))
                .tags(new ArrayList<>())
                .build();

        applyTags(doc, request.tags());
        doc = documentRepository.save(doc);

        String safeName = sanitizeFileName(Optional.ofNullable(file.getOriginalFilename()).orElse("document.bin"));
        String path;
        try {
            path = fileStorageService.store(content, doc.getUuid(), safeName);
        } catch (IOException e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "STORAGE_ERROR", "Stockage du fichier impossible");
        }

        doc.setOriginalPath(path);
        doc.setMimeType(detected);
        doc.setFileSize(file.getSize());
        doc = documentRepository.save(doc);

        ocrJobService.enqueue(doc.getId());
        auditService.log("DOCUMENT_UPLOAD", uploader, "DOCUMENT", doc.getId(), java.util.Map.of("uuid", doc.getUuid().toString()));

        return documentMapper.toDto(reloadForDto(doc.getId()));
    }

    @Transactional
    public List<DocumentDto> importBatch(List<MultipartFile> files, UploadRequest template, UserPrincipal principal) {
        if (files == null || files.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FILES_REQUIRED", "Au moins un fichier requis");
        }
        List<DocumentDto> out = new ArrayList<>();
        int i = 0;
        for (MultipartFile f : files) {
            String title = files.size() == 1
                    ? template.title()
                    : template.title() + " — " + sanitizeFileName(Optional.ofNullable(f.getOriginalFilename()).orElse("fichier-" + (++i)));
            UploadRequest req = new UploadRequest(
                    title,
                    template.documentTypeId(),
                    template.folderNumber(),
                    template.documentDate(),
                    template.language(),
                    template.confidentialityLevel(),
                    template.departmentId(),
                    template.externalReference(),
                    template.author(),
                    template.notes(),
                    template.tags()
            );
            out.add(upload(f, req, principal));
        }
        return out;
    }

    private void validateMime(String detected) {
        if (detected == null || ALLOWED_MIMES.stream().noneMatch(m -> m.equalsIgnoreCase(detected))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_MIME", "Format non supporté: " + detected);
        }
    }

    private void applyTags(Document doc, List<String> tags) {
        if (tags == null) {
            return;
        }
        for (String t : tags) {
            if (t == null || t.isBlank()) {
                continue;
            }
            DocumentTag tag = DocumentTag.builder().document(doc).tag(t.trim()).build();
            doc.getTags().add(tag);
        }
    }

    private Department resolveDepartment(Long departmentId, Long userId) {
        if (departmentId != null) {
            return departmentRepository.getReferenceById(departmentId);
        }
        User u = userRepository.findById(userId).orElseThrow();
        return u.getDepartment();
    }

    private Document reloadForDto(Long id) {
        return documentRepository.findDetailById(id).orElseThrow();
    }

    private static String sanitizeFileName(String name) {
        String base = name.replace("..", "").trim();
        if (base.isEmpty()) {
            return "document.bin";
        }
        return base.length() > 200 ? base.substring(0, 200) : base;
    }
}
