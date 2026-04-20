package com.archivage.ocr;

import com.archivage.common.domain.DocumentLanguage;
import com.archivage.common.domain.DocumentStatus;
import com.archivage.config.AppOcrProperties;
import com.archivage.document.entity.Document;
import com.archivage.document.repository.DocumentRepository;
import com.archivage.metadata.entity.DocumentTypeEntity;
import com.archivage.ocr.dto.OcrQueueStatsDto;
import com.archivage.ocr.entity.OcrJob;
import com.archivage.ocr.repository.OcrJobRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OcrJobServiceTest {

    @Mock
    private OcrJobRepository ocrJobRepository;
    @Mock
    private DocumentRepository documentRepository;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    private AppOcrProperties ocrProperties = new AppOcrProperties(1, "fra", 30, 5, false, false);

    private OcrJobService service;

    @BeforeEach
    void setUp() {
        service = new OcrJobService(ocrJobRepository, documentRepository, eventPublisher, ocrProperties);
    }

    @Test
    void enqueue_createsPendingJobAndPublishesEvent() {
        DocumentTypeEntity type = DocumentTypeEntity.builder()
                .code("T")
                .labelFr("L")
                .labelPt("L")
                .active(true)
                .build();
        type.setId(1L);
        Document doc = Document.builder()
                .title("t")
                .documentType(type)
                .folderNumber("F")
                .documentDate(java.time.LocalDate.now())
                .status(DocumentStatus.VALIDATED)
                .language(DocumentLanguage.FRENCH)
                .confidentialityLevel(com.archivage.common.domain.ConfidentialityLevel.INTERNAL)
                .build();
        doc.setId(10L);

        when(documentRepository.findActiveById(10L)).thenReturn(Optional.of(doc));
        when(ocrJobRepository.save(any(OcrJob.class))).thenAnswer(inv -> {
            OcrJob j = inv.getArgument(0);
            j.setId(100L);
            return j;
        });

        OcrJob job = service.enqueue(10L);

        assertThat(job.getId()).isEqualTo(100L);
        assertThat(job.getStatus()).isEqualTo(com.archivage.common.domain.OcrJobStatus.PENDING);
        ArgumentCaptor<OcrJobCreatedEvent> ev = ArgumentCaptor.forClass(OcrJobCreatedEvent.class);
        verify(eventPublisher).publishEvent(ev.capture());
        assertThat(ev.getValue().jobId()).isEqualTo(100L);
    }

    @Test
    void getQueueStats_returnsCounts() {
        when(ocrJobRepository.countByStatus(com.archivage.common.domain.OcrJobStatus.PENDING)).thenReturn(1L);
        when(ocrJobRepository.countByStatus(com.archivage.common.domain.OcrJobStatus.PROCESSING)).thenReturn(2L);
        when(ocrJobRepository.countByStatus(com.archivage.common.domain.OcrJobStatus.OCR_FAILED)).thenReturn(3L);
        when(ocrJobRepository.countByStatus(com.archivage.common.domain.OcrJobStatus.CANCELLED)).thenReturn(4L);

        OcrQueueStatsDto o = service.getQueueStats();

        assertThat(o.pending()).isEqualTo(1L);
        assertThat(o.processing()).isEqualTo(2L);
        assertThat(o.failed()).isEqualTo(3L);
        assertThat(o.cancelled()).isEqualTo(4L);
    }
}
