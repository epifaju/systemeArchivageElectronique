package com.archivage.admin;

import com.archivage.admin.dto.AuditLogAdminDto;
import com.archivage.admin.dto.SystemSettingsDto;
import com.archivage.admin.dto.CreateDocumentTypeRequest;
import com.archivage.admin.dto.CreateUserRequest;
import com.archivage.admin.dto.DashboardDto;
import com.archivage.admin.dto.DocumentTypeAdminDto;
import com.archivage.admin.dto.OcrJobAdminDto;
import com.archivage.ocr.dto.OcrQueueStatsDto;
import com.archivage.admin.dto.UpdateUserRequest;
import com.archivage.admin.dto.UserAdminDto;
import com.archivage.common.dto.PageResponseDto;
import com.archivage.document.DocumentService;
import com.archivage.document.dto.DocumentDto;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import com.archivage.auth.security.UserPrincipal;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;
    private final DocumentService documentService;
    private final SystemSettingsService systemSettingsService;

    @GetMapping("/users")
    @PreAuthorize("hasRole('ADMIN')")
    public PageResponseDto<UserAdminDto> listUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return adminService.listUsers(page, size);
    }

    @PostMapping("/users")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserAdminDto> createUser(
            @Valid @RequestBody CreateUserRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ResponseEntity.status(201).body(adminService.createUser(request, principal.getUser()));
    }

    @PutMapping("/users/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public UserAdminDto updateUser(
            @PathVariable Long id,
            @Valid @RequestBody UpdateUserRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return adminService.updateUser(id, request, principal.getUser());
    }

    @GetMapping("/document-types")
    @PreAuthorize("hasRole('ADMIN')")
    public PageResponseDto<DocumentTypeAdminDto> listDocumentTypes(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size
    ) {
        return adminService.listDocumentTypes(page, size);
    }

    @PostMapping("/document-types")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<DocumentTypeAdminDto> createDocumentType(
            @Valid @RequestBody CreateDocumentTypeRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ResponseEntity.status(201).body(adminService.createDocumentType(request, principal.getUser()));
    }

    @GetMapping("/dashboard")
    @PreAuthorize("hasRole('ADMIN')")
    public DashboardDto dashboard() {
        return adminService.dashboard();
    }

    /**
     * Paramètres d’exploitation (lecture seule) — aligné PRD §9 Administration / SystemSettings.
     */
    @GetMapping("/system-settings")
    @PreAuthorize("hasRole('ADMIN')")
    public SystemSettingsDto systemSettings() {
        return systemSettingsService.get();
    }

    @GetMapping("/ocr-queue/stats")
    @PreAuthorize("hasAnyRole('ADMIN','ARCHIVISTE')")
    public OcrQueueStatsDto ocrQueueStats() {
        return adminService.ocrQueueStats();
    }

    @GetMapping("/ocr-queue")
    @PreAuthorize("hasAnyRole('ADMIN','ARCHIVISTE')")
    public PageResponseDto<OcrJobAdminDto> ocrQueue(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return adminService.ocrQueue(page, size);
    }

    @PostMapping("/ocr-queue/{jobId}/cancel")
    @PreAuthorize("hasAnyRole('ADMIN','ARCHIVISTE')")
    public ResponseEntity<Void> cancelOcrJob(@PathVariable Long jobId) {
        adminService.cancelPendingOcrJob(jobId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/documents/deleted")
    @PreAuthorize("hasRole('ADMIN')")
    public PageResponseDto<DocumentDto> listDeletedDocuments(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return documentService.listDeletedForAdmin(page, size);
    }

    @PostMapping("/documents/{id}/restore")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> restoreDocument(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        documentService.restoreDeleted(id, principal);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/audit-logs")
    @PreAuthorize("hasAnyRole('ADMIN','AUDITEUR')")
    public PageResponseDto<AuditLogAdminDto> auditLogs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String resourceType,
            @RequestParam(required = false, defaultValue = "UTC") String timeZone
    ) {
        return adminService.auditLogs(page, size, dateFrom, dateTo, action, userId, resourceType, timeZone);
    }
}
