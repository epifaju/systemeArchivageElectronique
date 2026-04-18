package com.archivage.dashboard;

import com.archivage.auth.security.UserPrincipal;
import com.archivage.common.domain.DocumentStatus;
import com.archivage.common.domain.Role;
import com.archivage.common.exception.ApiException;
import com.archivage.dashboard.dto.HomeDashboardDto;
import com.archivage.document.repository.DocumentRepository;
import com.archivage.ocr.OcrJobService;
import com.archivage.ocr.dto.OcrQueueStatsDto;
import com.archivage.user.entity.User;
import com.archivage.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final UserRepository userRepository;
    private final DocumentRepository documentRepository;
    private final OcrJobService ocrJobService;

    @Transactional(readOnly = true)
    public HomeDashboardDto home(UserPrincipal principal) {
        User reader = userRepository.findWithDepartmentById(principal.getUser().getId())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Utilisateur introuvable"));

        long total = documentRepository.countVisibleForReader(reader);
        Map<DocumentStatus, Long> byStatusEnum = documentRepository.countByStatusVisibleForReader(reader);
        Instant since = Instant.now().minus(7, ChronoUnit.DAYS);
        long last7 = documentRepository.countCreatedSinceVisibleForReader(reader, since);

        Map<String, Long> byStatus = new LinkedHashMap<>();
        for (DocumentStatus s : DocumentStatus.values()) {
            byStatus.put(s.name(), byStatusEnum.getOrDefault(s, 0L));
        }

        OcrQueueStatsDto ocr = null;
        Role r = reader.getRole();
        if (r == Role.ADMIN || r == Role.ARCHIVISTE) {
            ocr = ocrJobService.getQueueStats();
        }

        String welcomeName = reader.getFullName() != null && !reader.getFullName().isBlank()
                ? reader.getFullName()
                : reader.getUsername();

        return new HomeDashboardDto(welcomeName, total, byStatus, last7, ocr);
    }
}
