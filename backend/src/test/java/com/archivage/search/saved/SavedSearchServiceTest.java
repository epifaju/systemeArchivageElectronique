package com.archivage.search.saved;

import com.archivage.auth.security.UserPrincipal;
import com.archivage.common.domain.Role;
import com.archivage.common.exception.ApiException;
import com.archivage.search.saved.dto.CreateSavedSearchRequest;
import com.archivage.search.saved.dto.UpdateSavedSearchRequest;
import com.archivage.search.saved.entity.SavedSearch;
import com.archivage.user.entity.User;
import com.archivage.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SavedSearchServiceTest {

    @Mock
    private SavedSearchRepository savedSearchRepository;
    @Mock
    private UserRepository userRepository;

    private SavedSearchService service;

    @BeforeEach
    void setUp() {
        service = new SavedSearchService(savedSearchRepository, userRepository);
    }

    @Test
    void list_returnsDtos() {
        User u = User.builder().username("a").passwordHash("x").role(Role.LECTEUR).active(true).build();
        u.setId(1L);
        UserPrincipal principal = new UserPrincipal(u);

        SavedSearch e = SavedSearch.builder()
                .user(u)
                .name("n")
                .criteria(Map.of("q", "x"))
                .build();
        e.setId(10L);
        e.setUuid(UUID.randomUUID());

        when(savedSearchRepository.findByUserIdOrderByUpdatedAtDesc(1L)).thenReturn(List.of(e));

        assertThat(service.list(principal)).hasSize(1);
        assertThat(service.list(principal).getFirst().name()).isEqualTo("n");
    }

    @Test
    void create_whenLimitReached_throws() {
        User u = User.builder().username("a").passwordHash("x").role(Role.LECTEUR).active(true).build();
        u.setId(1L);
        UserPrincipal principal = new UserPrincipal(u);
        when(savedSearchRepository.countByUserId(1L)).thenReturn(50L);

        assertThatThrownBy(() -> service.create(
                new CreateSavedSearchRequest("n", Map.of("q", "x")),
                principal
        )).isInstanceOf(ApiException.class);
    }

    @Test
    void create_whenUnderLimit_saves() {
        User u = User.builder().username("a").passwordHash("x").role(Role.LECTEUR).active(true).build();
        u.setId(1L);
        UserPrincipal principal = new UserPrincipal(u);
        when(savedSearchRepository.countByUserId(1L)).thenReturn(0L);
        when(userRepository.getReferenceById(1L)).thenReturn(u);
        when(savedSearchRepository.save(any(SavedSearch.class))).thenAnswer(inv -> {
            SavedSearch s = inv.getArgument(0);
            s.setId(3L);
            s.setUuid(UUID.randomUUID());
            return s;
        });

        var dto = service.create(new CreateSavedSearchRequest("nom", Map.of("q", "v")), principal);

        assertThat(dto.id()).isEqualTo(3L);
        verify(savedSearchRepository).save(any(SavedSearch.class));
    }

    @Test
    void update_emptyUpdate_throws() {
        User u = User.builder().username("a").passwordHash("x").role(Role.LECTEUR).active(true).build();
        u.setId(1L);
        UserPrincipal principal = new UserPrincipal(u);
        SavedSearch e = SavedSearch.builder().user(u).name("n").criteria(Map.of()).build();
        e.setId(5L);
        when(savedSearchRepository.findByIdAndUserId(5L, 1L)).thenReturn(Optional.of(e));

        assertThatThrownBy(() -> service.update(5L, new UpdateSavedSearchRequest(null, null), principal))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void update_notFound_throws() {
        User u = User.builder().username("a").passwordHash("x").role(Role.LECTEUR).active(true).build();
        u.setId(1L);
        UserPrincipal principal = new UserPrincipal(u);
        when(savedSearchRepository.findByIdAndUserId(99L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(
                99L,
                new UpdateSavedSearchRequest("new name", null),
                principal
        ))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException e = (ApiException) ex;
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(e.getCode()).isEqualTo("NOT_FOUND");
                });
    }

    @Test
    void delete_notFound_throws() {
        User u = User.builder().username("a").passwordHash("x").role(Role.LECTEUR).active(true).build();
        u.setId(1L);
        UserPrincipal principal = new UserPrincipal(u);
        when(savedSearchRepository.findByIdAndUserId(99L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(99L, principal))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException e = (ApiException) ex;
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(e.getCode()).isEqualTo("NOT_FOUND");
                });
    }

    @Test
    void delete_removesEntity() {
        User u = User.builder().username("a").passwordHash("x").role(Role.LECTEUR).active(true).build();
        u.setId(1L);
        UserPrincipal principal = new UserPrincipal(u);
        SavedSearch e = SavedSearch.builder().user(u).name("n").criteria(Map.of()).build();
        e.setId(5L);
        when(savedSearchRepository.findByIdAndUserId(5L, 1L)).thenReturn(Optional.of(e));

        service.delete(5L, principal);

        verify(savedSearchRepository).delete(e);
    }
}
