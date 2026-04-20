package com.archivage.ocr.repository;

import com.archivage.common.domain.OcrJobStatus;
import com.archivage.ocr.entity.OcrJob;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface OcrJobRepository extends JpaRepository<OcrJob, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select j from OcrJob j where j.id = :id")
    Optional<OcrJob> findByIdForUpdate(@Param("id") Long id);

    long countByStatus(OcrJobStatus status);

    Page<OcrJob> findByStatus(OcrJobStatus status, Pageable pageable);

    Page<OcrJob> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
