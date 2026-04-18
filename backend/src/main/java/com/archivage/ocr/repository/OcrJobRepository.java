package com.archivage.ocr.repository;

import com.archivage.common.domain.OcrJobStatus;
import com.archivage.ocr.entity.OcrJob;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OcrJobRepository extends JpaRepository<OcrJob, Long> {

    long countByStatus(OcrJobStatus status);

    Page<OcrJob> findByStatus(OcrJobStatus status, Pageable pageable);

    Page<OcrJob> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
