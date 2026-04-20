package com.archivage.admin;

import com.archivage.admin.dto.AuditLogAdminDto;
import com.archivage.admin.dto.CreateDocumentTypeRequest;
import com.archivage.admin.dto.CreateUserRequest;
import com.archivage.admin.dto.DashboardDto;
import com.archivage.admin.dto.DocumentTypeAdminDto;
import com.archivage.admin.dto.OcrJobAdminDto;
import com.archivage.admin.dto.UpdateUserRequest;
import com.archivage.admin.dto.UserAdminDto;
import com.archivage.audit.AuditLogSpecifications;
import com.archivage.audit.AuditService;
import com.archivage.audit.entity.AuditLog;
import com.archivage.audit.repository.AuditLogRepository;
import com.archivage.common.domain.DocumentStatus;
import com.archivage.common.dto.PageResponseDto;
import com.archivage.common.exception.ApiException;
import com.archivage.document.repository.DocumentRepository;
import com.archivage.metadata.entity.DocumentTypeEntity;
import com.archivage.metadata.repository.DocumentTypeRepository;
import com.archivage.common.domain.OcrJobStatus;
import com.archivage.ocr.dto.OcrQueueStatsDto;
import com.archivage.ocr.entity.OcrJob;
import com.archivage.ocr.repository.OcrJobRepository;
import com.archivage.user.entity.User;
import com.archivage.user.repository.DepartmentRepository;
import com.archivage.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AdminService {

    private final UserRepository userRepository;
    private final DepartmentRepository departmentRepository;
    private final DocumentTypeRepository documentTypeRepository;
    private final DocumentRepository documentRepository;
    private final OcrJobRepository ocrJobRepository;
    private final AuditLogRepository auditLogRepository;
    private final AuditService auditService;
    private final PasswordEncoder passwordEncoder;

    @Transactional(readOnly = true)
    public PageResponseDto<UserAdminDto> listUsers(int page, int size) {
        Page<User> users = userRepository.findAll(PageRequest.of(page, size));
        return PageResponseDto.of(users.map(this::toUserDto));
    }

    @Transactional
    public UserAdminDto createUser(CreateUserRequest request, User actor) {
        if (userRepository.findByUsername(request.username()).isPresent()) {
            throw new ApiException(HttpStatus.CONFLICT, "USERNAME_TAKEN", "Nom d'utilisateur déjà utilisé");
        }
        User user = User.builder()
                .username(request.username())
                .passwordHash(passwordEncoder.encode(request.password()))
                .email(request.email())
                .fullName(request.fullName())
                .role(request.role())
                .department(request.departmentId() != null
                        ? departmentRepository.getReferenceById(request.departmentId())
                        : null)
                .active(request.active())
                .build();
        user = userRepository.save(user);
        auditService.log("ADMIN_USER_CREATE", actor, "USER", user.getId(), Map.of("username", user.getUsername()));
        return toUserDto(user);
    }

    @Transactional
    public UserAdminDto updateUser(Long id, UpdateUserRequest request, User actor) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Utilisateur introuvable"));
        if (request.email() != null) {
            user.setEmail(request.email());
        }
        if (request.fullName() != null) {
            user.setFullName(request.fullName());
        }
        if (request.role() != null) {
            user.setRole(request.role());
        }
        if (request.departmentId() != null) {
            user.setDepartment(departmentRepository.getReferenceById(request.departmentId()));
        }
        if (request.active() != null) {
            user.setActive(request.active());
        }
        if (request.password() != null && !request.password().isBlank()) {
            user.setPasswordHash(passwordEncoder.encode(request.password()));
        }
        userRepository.save(user);
        auditService.log("ADMIN_USER_UPDATE", actor, "USER", id, Map.of("fields", "profile"));
        return toUserDto(user);
    }

    @Transactional(readOnly = true)
    public PageResponseDto<DocumentTypeAdminDto> listDocumentTypes(int page, int size) {
        Page<DocumentTypeEntity> pageResult = documentTypeRepository.findAll(PageRequest.of(page, size));
        return PageResponseDto.of(pageResult.map(dt -> new DocumentTypeAdminDto(
                dt.getId(), dt.getCode(), dt.getLabelFr(), dt.getLabelPt(), Boolean.TRUE.equals(dt.getActive()),
                dt.getCustomFieldsSchema()
        )));
    }

    @Transactional
    public DocumentTypeAdminDto createDocumentType(CreateDocumentTypeRequest request, User actor) {
        if (documentTypeRepository.findByCode(request.code()).isPresent()) {
            throw new ApiException(HttpStatus.CONFLICT, "CODE_TAKEN", "Code déjà utilisé");
        }
        DocumentTypeEntity entity = DocumentTypeEntity.builder()
                .code(request.code())
                .labelFr(request.labelFr())
                .labelPt(request.labelPt())
                .active(request.active())
                .customFieldsSchema(request.customFieldsSchema())
                .build();
        entity = documentTypeRepository.save(entity);
        auditService.log("ADMIN_DOC_TYPE_CREATE", actor, "DOCUMENT_TYPE", entity.getId(), Map.of("code", entity.getCode()));
        return new DocumentTypeAdminDto(
                entity.getId(), entity.getCode(), entity.getLabelFr(), entity.getLabelPt(), Boolean.TRUE.equals(entity.getActive()),
                entity.getCustomFieldsSchema()
        );
    }

    @Transactional(readOnly = true)
    public DashboardDto dashboard() {
        long total = documentRepository.countActiveDocuments();
        Map<DocumentStatus, Long> byStatus = new EnumMap<>(DocumentStatus.class);
        for (DocumentStatus s : DocumentStatus.values()) {
            long c = documentRepository.count((root, q, cb) -> cb.and(
                    cb.equal(root.get("status"), s),
                    cb.isFalse(root.get("isDeleted"))
            ));
            byStatus.put(s, c);
        }
        return new DashboardDto(total, byStatus, ocrQueueStats());
    }

    @Transactional(readOnly = true)
    public OcrQueueStatsDto ocrQueueStats() {
        return new OcrQueueStatsDto(
                ocrJobRepository.countByStatus(OcrJobStatus.PENDING),
                ocrJobRepository.countByStatus(OcrJobStatus.PROCESSING),
                ocrJobRepository.countByStatus(OcrJobStatus.OCR_FAILED),
                ocrJobRepository.countByStatus(OcrJobStatus.CANCELLED)
        );
    }

    /**
     * Annule un job encore en file (PENDING). Verrou pessimiste aligné avec {@link com.archivage.ocr.OcrWorker}.
     */
    @Transactional
    public void cancelPendingOcrJob(Long jobId) {
        OcrJob job = ocrJobRepository.findByIdForUpdate(jobId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Job OCR introuvable"));
        if (job.getStatus() != OcrJobStatus.PENDING) {
            throw new ApiException(HttpStatus.CONFLICT, "OCR_JOB_NOT_CANCELLABLE",
                    "Seuls les jobs en attente (PENDING) peuvent être annulés");
        }
        job.setStatus(OcrJobStatus.CANCELLED);
        job.setCompletedAt(Instant.now());
        job.setErrorMessage(null);
        ocrJobRepository.save(job);
    }

    @Transactional(readOnly = true)
    public PageResponseDto<OcrJobAdminDto> ocrQueue(int page, int size) {
        Page<OcrJob> jobs = ocrJobRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(page, size));
        return PageResponseDto.of(jobs.map(j -> new OcrJobAdminDto(
                j.getId(),
                j.getDocument().getId(),
                j.getStatus(),
                j.getStartedAt(),
                j.getCompletedAt(),
                j.getRetryCount(),
                j.getErrorMessage()
        )));
    }

    @Transactional(readOnly = true)
    public PageResponseDto<AuditLogAdminDto> auditLogs(
            int page,
            int size,
            LocalDate dateFrom,
            LocalDate dateTo,
            String action,
            Long userId,
            String resourceType,
            String timeZoneId
    ) {
        LocalDate from = dateFrom;
        LocalDate to = dateTo;
        if (from != null && to != null && from.isAfter(to)) {
            LocalDate tmp = from;
            from = to;
            to = tmp;
        }
        ZoneId zone = resolveAuditFilterZone(timeZoneId);
        Specification<AuditLog> spec = AuditLogSpecifications.filtered(from, to, action, userId, resourceType, zone);
        Page<AuditLog> logs = auditLogRepository.findAll(
                spec,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))
        );
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

    /** Fuseau IANA (ex. Europe/Paris) pour interpréter les dates « jour calendaire » ; invalide → UTC. */
    private static ZoneId resolveAuditFilterZone(String timeZoneId) {
        if (timeZoneId == null || timeZoneId.isBlank()) {
            return ZoneId.of("UTC");
        }
        try {
            return ZoneId.of(timeZoneId.trim());
        } catch (DateTimeException e) {
            return ZoneId.of("UTC");
        }
    }

    private UserAdminDto toUserDto(User u) {
        return new UserAdminDto(
                u.getId(),
                u.getUuid(),
                u.getUsername(),
                u.getEmail(),
                u.getFullName(),
                u.getRole(),
                u.getDepartment() != null ? u.getDepartment().getId() : null,
                Boolean.TRUE.equals(u.getActive())
        );
    }
}
