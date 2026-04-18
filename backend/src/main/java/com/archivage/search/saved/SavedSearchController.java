package com.archivage.search.saved;

import com.archivage.auth.security.UserPrincipal;
import com.archivage.search.saved.dto.CreateSavedSearchRequest;
import com.archivage.search.saved.dto.SavedSearchDto;
import com.archivage.search.saved.dto.UpdateSavedSearchRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/saved-searches")
@RequiredArgsConstructor
public class SavedSearchController {

    private final SavedSearchService savedSearchService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','ARCHIVISTE','AGENT','LECTEUR')")
    public List<SavedSearchDto> list(@AuthenticationPrincipal UserPrincipal principal) {
        return savedSearchService.list(principal);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','ARCHIVISTE','AGENT','LECTEUR')")
    @ResponseStatus(HttpStatus.CREATED)
    public SavedSearchDto create(
            @Valid @RequestBody CreateSavedSearchRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return savedSearchService.create(request, principal);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','ARCHIVISTE','AGENT','LECTEUR')")
    public SavedSearchDto update(
            @PathVariable Long id,
            @Valid @RequestBody UpdateSavedSearchRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return savedSearchService.update(id, request, principal);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','ARCHIVISTE','AGENT','LECTEUR')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id, @AuthenticationPrincipal UserPrincipal principal) {
        savedSearchService.delete(id, principal);
    }
}
