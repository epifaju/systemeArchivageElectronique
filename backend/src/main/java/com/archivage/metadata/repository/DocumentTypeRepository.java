package com.archivage.metadata.repository;

import com.archivage.metadata.entity.DocumentTypeEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DocumentTypeRepository extends JpaRepository<DocumentTypeEntity, Long> {

    Optional<DocumentTypeEntity> findByCode(String code);

    List<DocumentTypeEntity> findByActiveTrueOrderByCodeAsc();
}
