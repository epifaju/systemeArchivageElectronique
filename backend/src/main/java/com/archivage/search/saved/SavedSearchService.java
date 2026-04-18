package com.archivage.search.saved;

import com.archivage.auth.security.UserPrincipal;
import com.archivage.common.exception.ApiException;
import com.archivage.search.saved.dto.CreateSavedSearchRequest;
import com.archivage.search.saved.dto.SavedSearchDto;
import com.archivage.search.saved.dto.UpdateSavedSearchRequest;
import com.archivage.search.saved.entity.SavedSearch;
import com.archivage.user.entity.User;
import com.archivage.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class SavedSearchService {

    private static final int MAX_SAVED_PER_USER = 50;

    private final SavedSearchRepository savedSearchRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<SavedSearchDto> list(UserPrincipal principal) {
        return savedSearchRepository.findByUserIdOrderByUpdatedAtDesc(principal.getUser().getId()).stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional
    public SavedSearchDto create(CreateSavedSearchRequest request, UserPrincipal principal) {
        Long userId = principal.getUser().getId();
        if (savedSearchRepository.countByUserId(userId) >= MAX_SAVED_PER_USER) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "SAVED_SEARCH_LIMIT",
                    "Nombre maximum de recherches sauvegardées atteint (" + MAX_SAVED_PER_USER + ")"
            );
        }
        User user = userRepository.getReferenceById(userId);
        Map<String, Object> criteria = new LinkedHashMap<>(request.criteria());
        SavedSearch entity = SavedSearch.builder()
                .user(user)
                .name(request.name().trim())
                .criteria(criteria)
                .build();
        savedSearchRepository.save(entity);
        return toDto(entity);
    }

    @Transactional
    public SavedSearchDto update(Long id, UpdateSavedSearchRequest request, UserPrincipal principal) {
        SavedSearch entity = savedSearchRepository.findByIdAndUserId(id, principal.getUser().getId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Recherche sauvegardée introuvable"));
        boolean hasName = request.name() != null && !request.name().isBlank();
        boolean hasCriteria = request.criteria() != null;
        if (!hasName && !hasCriteria) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "EMPTY_UPDATE", "Indiquez un nom ou des critères à modifier");
        }
        if (hasName) {
            entity.setName(request.name().trim());
        }
        if (hasCriteria) {
            entity.setCriteria(new LinkedHashMap<>(request.criteria()));
        }
        savedSearchRepository.save(entity);
        return toDto(entity);
    }

    @Transactional
    public void delete(Long id, UserPrincipal principal) {
        SavedSearch entity = savedSearchRepository.findByIdAndUserId(id, principal.getUser().getId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Recherche sauvegardée introuvable"));
        savedSearchRepository.delete(entity);
    }

    private SavedSearchDto toDto(SavedSearch e) {
        return new SavedSearchDto(
                e.getId(),
                e.getUuid(),
                e.getName(),
                e.getCriteria(),
                e.getCreatedAt(),
                e.getUpdatedAt()
        );
    }
}
