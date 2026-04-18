package com.archivage.ocr;

import com.archivage.common.domain.OcrJobStatus;
import com.archivage.config.AppOcrProperties;
import com.archivage.document.entity.Document;
import com.archivage.document.repository.DocumentRepository;
import com.archivage.ocr.dto.OcrQueueStatsDto;
import com.archivage.ocr.entity.OcrJob;
import com.archivage.ocr.repository.OcrJobRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OcrJobService {

    private final OcrJobRepository ocrJobRepository;
    private final DocumentRepository documentRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final AppOcrProperties ocrProperties;

    @Transactional
    public OcrJob enqueue(Long documentId) {
        Document document = documentRepository.findActiveById(documentId).orElseThrow();
        OcrJob job = OcrJob.builder()
                .document(document)
                .status(OcrJobStatus.PENDING)
                .ocrEngine("tesseract")
                .ocrLang(OcrWorker.resolveOcrLang(document.getLanguage(), ocrProperties.langDefault()))
                .retryCount(0)
                .build();
        job = ocrJobRepository.save(job);
        eventPublisher.publishEvent(new OcrJobCreatedEvent(job.getId()));
        return job;
    }

    public OcrQueueStatsDto getQueueStats() {
        long pending = ocrJobRepository.countByStatus(OcrJobStatus.PENDING);
        long processing = ocrJobRepository.countByStatus(OcrJobStatus.PROCESSING);
        long failed = ocrJobRepository.countByStatus(OcrJobStatus.OCR_FAILED);
        return new OcrQueueStatsDto(pending, processing, failed);
    }
}
