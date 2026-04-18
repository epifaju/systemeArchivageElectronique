package com.archivage.search.saved;

import com.archivage.search.saved.entity.SavedSearch;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SavedSearchRepository extends JpaRepository<SavedSearch, Long> {

    List<SavedSearch> findByUserIdOrderByUpdatedAtDesc(Long userId);

    Optional<SavedSearch> findByIdAndUserId(Long id, Long userId);

    long countByUserId(Long userId);
}
