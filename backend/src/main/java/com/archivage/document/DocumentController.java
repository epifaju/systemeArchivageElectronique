package com.archivage.document;

import com.archivage.auth.security.UserPrincipal;
import com.archivage.admin.dto.AuditLogAdminDto;
import com.archivage.common.domain.ConfidentialityLevel;
import com.archivage.common.domain.DocumentLanguage;
import com.archivage.common.domain.DocumentStatus;
import com.archivage.common.dto.PageResponseDto;
import com.archivage.document.dto.DocumentDto;
import com.archivage.document.dto.DocumentMetadataUpdateRequest;
import com.archivage.document.dto.DocumentStatusUpdateRequest;
import com.archivage.document.dto.UploadRequest;
import com.archivage.search.dto.SearchRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentUploadService documentUploadService;
    private final DocumentService documentService;

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('AGENT','ARCHIVISTE','ADMIN')")
    public ResponseEntity<DocumentDto> upload(
            @RequestPart("file") MultipartFile file,
            @Valid @RequestPart("metadata") UploadRequest metadata,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ResponseEntity.status(201).body(documentUploadService.upload(file, metadata, principal));
    }

    @PostMapping(value = "/import-batch", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('AGENT','ARCHIVISTE','ADMIN')")
    public ResponseEntity<List<DocumentDto>> importBatch(
            @RequestPart("files") List<MultipartFile> files,
            @Valid @RequestPart("metadata") UploadRequest metadata,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ResponseEntity.status(201).body(documentUploadService.importBatch(files, metadata, principal));
    }

    @PostMapping(value = "/import-zip", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('AGENT','ARCHIVISTE','ADMIN')")
    public ResponseEntity<List<DocumentDto>> importZip(
            @RequestPart("zip") MultipartFile zip,
            @Valid @RequestPart("metadata") UploadRequest metadata,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ResponseEntity.status(201).body(documentUploadService.importZip(zip, metadata, principal));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','ARCHIVISTE','AGENT','LECTEUR')")
    public PageResponseDto<DocumentDto> list(
            @RequestParam(required = false) Long documentTypeId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(required = false) String folderNumber,
            @RequestParam(required = false) DocumentLanguage language,
            @RequestParam(required = false) DocumentStatus status,
            @RequestParam(required = false) Long departmentId,
            @RequestParam(required = false) ConfidentialityLevel confidentialityLevel,
            @RequestParam(required = false) SearchRequest.SearchSort sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        SearchRequest req = new SearchRequest(
                null,
                documentTypeId,
                dateFrom,
                dateTo,
                folderNumber,
                language,
                status,
                departmentId,
                confidentialityLevel,
                sort,
                page,
                size
        );
        return documentService.list(req, principal);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','ARCHIVISTE','AGENT','LECTEUR')")
    public DocumentDto get(@PathVariable Long id, @AuthenticationPrincipal UserPrincipal principal) {
        return documentService.getById(id, principal);
    }

    @GetMapping("/{id}/history")
    @PreAuthorize("hasAnyRole('ADMIN','ARCHIVISTE','AGENT','LECTEUR')")
    public PageResponseDto<AuditLogAdminDto> documentHistory(
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "false") boolean includeViews,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return documentService.documentHistory(id, page, size, includeViews, principal);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ARCHIVISTE','ADMIN')")
    public ResponseEntity<Void> softDelete(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        documentService.softDelete(id, principal);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    @PutMapping("/{id}/metadata")
    @PreAuthorize("hasAnyRole('AGENT','ARCHIVISTE','ADMIN')")
    public DocumentDto updateMetadata(
            @PathVariable Long id,
            @Valid @RequestBody DocumentMetadataUpdateRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return documentService.updateMetadata(id, request, principal);
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('ARCHIVISTE','ADMIN')")
    public DocumentDto updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody DocumentStatusUpdateRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return documentService.updateStatus(id, request, principal);
    }

    @PostMapping("/{id}/reprocess-ocr")
    @PreAuthorize("hasAnyRole('ARCHIVISTE','ADMIN')")
    public ResponseEntity<Void> reprocess(@PathVariable Long id, @AuthenticationPrincipal UserPrincipal principal) {
        documentService.reprocessOcr(id, principal);
        return ResponseEntity.accepted().build();
    }

    @GetMapping("/{id}/download/original")
    @PreAuthorize("hasAnyRole('ADMIN','ARCHIVISTE','AGENT','LECTEUR')")
    public ResponseEntity<Resource> downloadOriginal(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        Resource resource = documentService.downloadOriginal(id, principal, true);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"original\"")
                .body(resource);
    }

    @GetMapping("/{id}/download/ocr")
    @PreAuthorize("hasAnyRole('ADMIN','ARCHIVISTE','AGENT','LECTEUR')")
    public ResponseEntity<Resource> downloadOcr(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        Resource resource = documentService.downloadOcr(id, principal, true);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"ocr.pdf\"")
                .body(resource);
    }

    @GetMapping("/{id}/preview")
    @PreAuthorize("hasAnyRole('ADMIN','ARCHIVISTE','AGENT','LECTEUR')")
    public ResponseEntity<Resource> preview(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        DocumentDto meta = documentService.getById(id, principal);
        Resource resource = documentService.downloadOriginal(id, principal, false);
        MediaType contentType = MediaType.APPLICATION_OCTET_STREAM;
        if (meta.mimeType() != null && !meta.mimeType().isBlank()) {
            try {
                contentType = MediaType.parseMediaType(meta.mimeType());
            } catch (IllegalArgumentException ignored) {
                // conserve APPLICATION_OCTET_STREAM
            }
        }
        return ResponseEntity.ok()
                .contentType(contentType)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"preview\"")
                .body(resource);
    }

    /** Aperçu du PDF OCR (inline). 404 si pas encore d'OCR. */
    @GetMapping("/{id}/preview/ocr")
    @PreAuthorize("hasAnyRole('ADMIN','ARCHIVISTE','AGENT','LECTEUR')")
    public ResponseEntity<Resource> previewOcr(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        Resource resource = documentService.downloadOcr(id, principal, false);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"ocr.pdf\"")
                .body(resource);
    }
}
