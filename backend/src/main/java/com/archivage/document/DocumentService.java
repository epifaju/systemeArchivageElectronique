package com.archivage.document;

import com.archivage.admin.dto.AuditLogAdminDto;
import com.archivage.audit.AuditLogSpecifications;
import com.archivage.audit.AuditService;
import com.archivage.audit.entity.AuditLog;
import com.archivage.audit.repository.AuditLogRepository;
import com.archivage.auth.security.UserPrincipal;
import com.archivage.common.domain.DocumentStatus;
import com.archivage.common.dto.PageResponseDto;
import com.archivage.common.exception.ApiException;
import com.archivage.document.dto.DocumentDto;
import com.archivage.document.dto.DocumentMetadataPatchRequest;
import com.archivage.document.dto.DocumentMetadataUpdateRequest;
import com.archivage.document.dto.MetadataSuggestionsDto;
import com.archivage.document.dto.DocumentStatusUpdateRequest;
import com.archivage.document.entity.Document;
import com.archivage.document.entity.DocumentTag;
import com.archivage.document.mapper.DocumentMapper;
import com.archivage.document.policy.DocumentAccessService;
import com.archivage.document.repository.DocumentRepository;
import com.archivage.metadata.entity.DocumentTypeEntity;
import com.archivage.metadata.repository.DocumentTypeRepository;
import com.archivage.metadata.validation.CustomMetadataValidator;
import com.archivage.ocr.OcrJobService;
import com.archivage.search.dto.SearchRequest;
import com.archivage.user.entity.User;
import com.archivage.user.repository.DepartmentRepository;
import com.archivage.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DocumentService {

    private final DocumentRepository documentRepository;
    private final DocumentTypeRepository documentTypeRepository;
    private final DocumentMapper documentMapper;
    private final AuditService auditService;
    private final OcrJobService ocrJobService;
    private final DepartmentRepository departmentRepository;
    private final UserRepository userRepository;
    private final DocumentAccessService documentAccessService;
    private final AuditLogRepository auditLogRepository;
    private final CustomMetadataValidator customMetadataValidator;
    private final MetadataSuggestionService metadataSuggestionService;

    private User loadReader(UserPrincipal principal) {
        return userRepository.findWithDepartmentById(principal.getUser().getId())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Utilisateur introuvable"));
    }

    @Transactional(readOnly = true)
    public DocumentDto getById(Long id, UserPrincipal principal) {
        User reader = loadReader(principal);
        Document doc = documentRepository.findDetailById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Document introuvable"));
        documentAccessService.assertCanRead(reader, doc);
        auditService.log("DOCUMENT_VIEW", reader, "DOCUMENT", id, Map.of());
        return documentMapper.toDto(doc);
    }

    @Transactional(readOnly = true)
    public MetadataSuggestionsDto getMetadataSuggestions(Long id, UserPrincipal principal) {
        User reader = loadReader(principal);
        Document doc = documentRepository.findDetailById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Document introuvable"));
        documentAccessService.assertCanRead(reader, doc);
        return metadataSuggestionService.suggestFromOcrText(doc.getOcrText());
    }

    @Transactional(readOnly = true)
    public DocumentDto getByUuid(UUID uuid, UserPrincipal principal) {
        User reader = loadReader(principal);
        Document doc = documentRepository.findByUuid(uuid)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Document introuvable"));
        Document detail = documentRepository.findDetailById(doc.getId()).orElse(doc);
        documentAccessService.assertCanRead(reader, detail);
        auditService.log("DOCUMENT_VIEW", reader, "DOCUMENT", detail.getId(), Map.of());
        return documentMapper.toDto(detail);
    }

    @Transactional(readOnly = true)
    public PageResponseDto<DocumentDto> list(SearchRequest filters, UserPrincipal principal) {
        User reader = loadReader(principal);
        Pageable pageable = PageRequest.of(
                filters.pageIndex(),
                filters.pageSize(),
                toListSort(filters)
        );
        Page<Document> page = documentRepository.searchDocuments(filters, pageable, reader);
        Page<DocumentDto> mapped = page.map(d -> documentMapper.toDto(
                documentRepository.findDetailById(d.getId()).orElse(d)
        ));
        return PageResponseDto.of(mapped);
    }

    @Transactional
    public DocumentDto updateMetadata(Long id, DocumentMetadataUpdateRequest request, UserPrincipal principal) {
        User reader = loadReader(principal);
        Document doc = documentRepository.findDetailById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Document introuvable"));
        documentAccessService.assertCanRead(reader, doc);
        boolean typeChanged = !Objects.equals(doc.getDocumentType().getId(), request.documentTypeId());
        DocumentTypeEntity type = documentTypeRepository.findById(request.documentTypeId())
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "INVALID_TYPE", "Type documentaire inconnu"));
        doc.setTitle(request.title());
        doc.setDocumentType(type);
        doc.setFolderNumber(request.folderNumber());
        doc.setDocumentDate(request.documentDate());
        doc.setLanguage(request.language());
        doc.setConfidentialityLevel(request.confidentialityLevel());
        doc.setExternalReference(request.externalReference());
        doc.setAuthor(request.author());
        doc.setNotes(request.notes());
        if (request.departmentId() != null) {
            doc.setDepartment(departmentRepository.getReferenceById(request.departmentId()));
        }
        doc.getTags().clear();
        if (request.tags() != null) {
            for (String t : request.tags()) {
                if (t != null && !t.isBlank()) {
                    doc.getTags().add(DocumentTag.builder().document(doc).tag(t.trim()).build());
                }
            }
        }
        if (request.customMetadata() != null) {
            doc.setCustomMetadata(customMetadataValidator.validateAndNormalize(type.getCustomFieldsSchema(), request.customMetadata()));
        } else if (typeChanged) {
            doc.setCustomMetadata(customMetadataValidator.validateAndNormalize(type.getCustomFieldsSchema(), Map.of()));
        }
        documentRepository.save(doc);
        auditService.log("DOCUMENT_UPDATE", principal.getUser(), "DOCUMENT", id, Map.of("fields", "metadata"));
        return documentMapper.toDto(documentRepository.findDetailById(id).orElseThrow());
    }

    @Transactional
    public DocumentDto patchMetadata(Long id, DocumentMetadataPatchRequest patch, UserPrincipal principal) {
        User reader = loadReader(principal);
        Document doc = documentRepository.findDetailById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Document introuvable"));
        documentAccessService.assertCanRead(reader, doc);
        DocumentMetadataUpdateRequest merged = mergeMetadataPatch(doc, patch);
        return updateMetadata(id, merged, principal);
    }

    private static DocumentMetadataUpdateRequest mergeMetadataPatch(Document doc, DocumentMetadataPatchRequest patch) {
        String title = patch.title() != null ? patch.title().trim() : doc.getTitle();
        if (title.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_TITLE", "Le titre est obligatoire");
        }
        Long documentTypeId = patch.documentTypeId() != null ? patch.documentTypeId() : doc.getDocumentType().getId();
        String folderNumber = patch.folderNumber() != null ? patch.folderNumber().trim() : doc.getFolderNumber();
        if (folderNumber.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_FOLDER", "Le numéro de dossier est obligatoire");
        }
        var docDate = patch.documentDate() != null ? patch.documentDate() : doc.getDocumentDate();
        var language = patch.language() != null ? patch.language() : doc.getLanguage();
        var conf = patch.confidentialityLevel() != null ? patch.confidentialityLevel() : doc.getConfidentialityLevel();
        Long departmentId = patch.departmentId() != null ? patch.departmentId()
                : (doc.getDepartment() != null ? doc.getDepartment().getId() : null);
        String ext = patch.externalReference() != null ? patch.externalReference() : doc.getExternalReference();
        String author = patch.author() != null ? patch.author() : doc.getAuthor();
        String notes = patch.notes() != null ? patch.notes() : doc.getNotes();
        List<String> tags = patch.tags() != null
                ? patch.tags()
                : doc.getTags().stream().map(DocumentTag::getTag).toList();
        Map<String, Object> custom = patch.customMetadata();
        return new DocumentMetadataUpdateRequest(
                title,
                documentTypeId,
                folderNumber,
                docDate,
                language,
                conf,
                departmentId,
                ext,
                author,
                notes,
                tags,
                custom
        );
    }

    @Transactional
    public DocumentDto updateStatus(Long id, DocumentStatusUpdateRequest request, UserPrincipal principal) {
        User reader = loadReader(principal);
        Document doc = documentRepository.findDetailById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Document introuvable"));
        documentAccessService.assertCanRead(reader, doc);
        doc.setStatus(request.status());
        documentRepository.save(doc);
        auditService.log("DOCUMENT_STATUS", principal.getUser(), "DOCUMENT", id, Map.of("status", request.status().name()));
        return documentMapper.toDto(documentRepository.findDetailById(id).orElseThrow());
    }

    /**
     * @param audit true uniquement pour les téléchargements explicites (pièce jointe), pas pour l’aperçu inline
     *              (évite une entrée d’audit à chaque chargement du PDF dans le viewer).
     */
    @Transactional
    public Resource downloadOriginal(Long id, UserPrincipal principal, boolean audit) {
        User reader = loadReader(principal);
        Document doc = documentRepository.findDetailById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Document introuvable"));
        documentAccessService.assertCanRead(reader, doc);
        Path path = Path.of(doc.getOriginalPath());
        if (audit && principal != null) {
            auditService.log("DOCUMENT_DOWNLOAD_ORIGINAL", principal.getUser(), "DOCUMENT", id, Map.of());
        }
        return new FileSystemResource(path);
    }

    @Transactional
    public Resource downloadOcr(Long id, UserPrincipal principal, boolean audit) {
        User reader = loadReader(principal);
        Document doc = documentRepository.findDetailById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Document introuvable"));
        documentAccessService.assertCanRead(reader, doc);
        if (doc.getOcrPath() == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "NO_OCR", "Aucun fichier OCR disponible");
        }
        if (audit && principal != null) {
            auditService.log("DOCUMENT_DOWNLOAD_OCR", principal.getUser(), "DOCUMENT", id, Map.of());
        }
        return new FileSystemResource(Path.of(doc.getOcrPath()));
    }

    @Transactional
    public void reprocessOcr(Long id, UserPrincipal principal) {
        User reader = loadReader(principal);
        Document doc = documentRepository.findDetailById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Document introuvable"));
        documentAccessService.assertCanRead(reader, doc);
        doc.setStatus(DocumentStatus.PENDING);
        documentRepository.save(doc);
        ocrJobService.enqueue(doc.getId());
        auditService.log("DOCUMENT_REPROCESS_OCR", principal.getUser(), "DOCUMENT", id, Map.of());
    }

    /**
     * Suppression logique : le document disparaît des listes et de la recherche ; fichiers conservés.
     */
    @Transactional
    public void softDelete(Long id, UserPrincipal principal) {
        User reader = loadReader(principal);
        Document doc = documentRepository.findDetailById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Document introuvable"));
        documentAccessService.assertCanRead(reader, doc);
        doc.setDeleted(true);
        doc.setDeletedAt(Instant.now());
        documentRepository.save(doc);
        auditService.log("DOCUMENT_SOFT_DELETE", principal.getUser(), "DOCUMENT", id, Map.of());
    }

    @Transactional
    public void restoreDeleted(Long id, UserPrincipal principal) {
        loadReader(principal);
        Document doc = documentRepository.findDeletedById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Document introuvable ou déjà actif"));
        doc.setDeleted(false);
        doc.setDeletedAt(null);
        documentRepository.save(doc);
        auditService.log("DOCUMENT_RESTORE", principal.getUser(), "DOCUMENT", id, Map.of());
    }

    @Transactional(readOnly = true)
    public PageResponseDto<AuditLogAdminDto> documentHistory(
            Long id,
            int page,
            int size,
            boolean includeViews,
            UserPrincipal principal
    ) {
        User reader = loadReader(principal);
        Document doc = documentRepository.findDetailById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Document introuvable"));
        documentAccessService.assertCanRead(reader, doc);
        Specification<AuditLog> spec = AuditLogSpecifications.forDocument(id, includeViews);
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<AuditLog> logs = auditLogRepository.findAll(spec, pageable);
        return PageResponseDto.of(logs.map(l -> new AuditLogAdminDto(
                l.getId(),
                l.getUser() != null ? l.getUser().getId() : null,
                l.getUser() != null ? l.getUser().getUsername() : null,
                l.getAction(),
                l.getResourceType(),
                l.getResourceId(),
                l.getDetails() != null ? l.getDetails() : Collections.emptyMap(),
                l.getIpAddress(),
                l.getCreatedAt()
        )));
    }

    @Transactional(readOnly = true)
    public PageResponseDto<DocumentDto> listDeletedForAdmin(int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "deletedAt"));
        Page<Document> p = documentRepository.findDeletedPage(pageable);
        return PageResponseDto.of(p.map(documentMapper::toDto));
    }

    private Sort toListSort(SearchRequest request) {
        if (request.sort() == null) {
            return Sort.by(Sort.Direction.DESC, "documentDate");
        }
        return switch (request.sort()) {
            case DATE_ASC -> Sort.by(Sort.Direction.ASC, "documentDate");
            case DATE_DESC -> Sort.by(Sort.Direction.DESC, "documentDate");
            case TITLE_ASC -> Sort.by(Sort.Direction.ASC, "title");
            case RELEVANCE -> Sort.by(Sort.Direction.DESC, "updatedAt");
            case CREATED_ASC -> Sort.by(Sort.Direction.ASC, "createdAt");
            case CREATED_DESC -> Sort.by(Sort.Direction.DESC, "createdAt");
        };
    }
}
