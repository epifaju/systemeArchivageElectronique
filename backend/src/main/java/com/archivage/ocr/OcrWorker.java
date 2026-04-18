package com.archivage.ocr;

import com.archivage.common.domain.DocumentStatus;
import com.archivage.common.domain.OcrJobStatus;
import com.archivage.config.AppOcrProperties;
import com.archivage.document.entity.Document;
import com.archivage.document.repository.DocumentRepository;
import com.archivage.ocr.entity.OcrJob;
import com.archivage.ocr.repository.OcrJobRepository;
import com.archivage.storage.FileStorageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class OcrWorker {

    private final OcrJobRepository ocrJobRepository;
    private final DocumentRepository documentRepository;
    private final FileStorageService fileStorageService;
    private final TextExtractionService textExtractionService;
    private final AppOcrProperties ocrProperties;
    private final ObjectProvider<OcrWorker> self;

    public OcrWorker(
            OcrJobRepository ocrJobRepository,
            DocumentRepository documentRepository,
            FileStorageService fileStorageService,
            TextExtractionService textExtractionService,
            AppOcrProperties ocrProperties,
            ObjectProvider<OcrWorker> self
    ) {
        this.ocrJobRepository = ocrJobRepository;
        this.documentRepository = documentRepository;
        this.fileStorageService = fileStorageService;
        this.textExtractionService = textExtractionService;
        this.ocrProperties = ocrProperties;
        this.self = self;
    }

    public void runWithRetries(Long jobId) {
        OcrJob job = ocrJobRepository.findById(jobId).orElse(null);
        if (job == null) {
            log.warn("OCR job {} introuvable en base (transaction non visible ou id invalide) — abandon", jobId);
            return;
        }
        int attempt = 0;
        while (attempt < ocrProperties.maxRetries()) {
            try {
                self.getObject().process(jobId);
                return;
            } catch (Exception ex) {
                attempt++;
                OcrJob fresh = ocrJobRepository.findById(jobId).orElseThrow();
                fresh.setRetryCount(attempt);
                ocrJobRepository.save(fresh);
                log.warn("OCR job {} tentative {}/{} : {}", jobId, attempt, ocrProperties.maxRetries(), ex.getMessage());
                if (attempt >= ocrProperties.maxRetries()) {
                    self.getObject().failJob(fresh, ex.getMessage());
                    return;
                }
                sleepBackoff(attempt);
            }
        }
    }

    private void sleepBackoff(int attempt) {
        long ms = (long) Math.min(60_000, 1000L * (1L << (attempt - 1)));
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Transactional
    public void process(Long jobId) throws Exception {
        OcrJob job = ocrJobRepository.findById(jobId).orElseThrow();
        Document document = documentRepository.findActiveById(job.getDocument().getId()).orElseThrow();

        job.setStatus(OcrJobStatus.PROCESSING);
        job.setStartedAt(Instant.now());
        document.setStatus(DocumentStatus.PROCESSING);
        ocrJobRepository.save(job);
        documentRepository.save(document);

        Path input = fileStorageService.resolvePath(document.getOriginalPath());
        if (!Files.exists(input)) {
            throw new IOException("Fichier original introuvable: " + input);
        }

        if (ocrProperties.mock() && document.getMimeType() != null && !document.getMimeType().contains("pdf")) {
            finishImageMock(job, document);
            return;
        }

        Path output = input.getParent().resolve("ocr-" + document.getUuid() + ".pdf");

        String logOutput;
        if (ocrProperties.mock()) {
            Files.copy(input, output, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            logOutput = "OCR mock: copie du PDF sans ocrmypdf";
        } else {
            logOutput = runOcrmypdf(input, output, job.getOcrLang());
        }

        job.setLogOutput(logOutput);

        String text;
        int pages;
        try {
            text = textExtractionService.extractPlainText(output);
            pages = textExtractionService.countPages(output);
        } catch (IOException ex) {
            throw new IOException("Extraction texte PDF impossible: " + ex.getMessage(), ex);
        }

        document.setOcrPath(output.toAbsolutePath().normalize().toString());
        document.setOcrText(text);
        document.setPageCount(pages);
        document.setStatus(mapDocumentStatus(text, pages));

        job.setStatus(mapJobStatus(document.getStatus()));
        job.setCompletedAt(Instant.now());
        job.setDurationMs(Duration.between(job.getStartedAt(), job.getCompletedAt()).toMillis());
        job.setPageCount(pages);

        ocrJobRepository.save(job);
        documentRepository.save(document);
    }

    private void finishImageMock(OcrJob job, Document document) {
        document.setOcrPath(document.getOriginalPath());
        document.setOcrText("Texte simulé (mode OCR mock — fichier image)");
        document.setPageCount(1);
        document.setStatus(DocumentStatus.OCR_SUCCESS);

        job.setStatus(OcrJobStatus.OCR_SUCCESS);
        job.setLogOutput("OCR mock: image sans conversion PDF");
        job.setCompletedAt(Instant.now());
        job.setDurationMs(Duration.between(job.getStartedAt(), job.getCompletedAt()).toMillis());
        job.setPageCount(1);

        ocrJobRepository.save(job);
        documentRepository.save(document);
    }

    private DocumentStatus mapDocumentStatus(String text, int pages) {
        if (pages <= 0) {
            return DocumentStatus.OCR_FAILED;
        }
        if (text == null || text.isBlank()) {
            return DocumentStatus.OCR_PARTIAL;
        }
        return DocumentStatus.OCR_SUCCESS;
    }

    private OcrJobStatus mapJobStatus(DocumentStatus documentStatus) {
        return switch (documentStatus) {
            case OCR_SUCCESS -> OcrJobStatus.OCR_SUCCESS;
            case OCR_PARTIAL, NEEDS_REVIEW -> OcrJobStatus.OCR_PARTIAL;
            case OCR_FAILED -> OcrJobStatus.OCR_FAILED;
            default -> OcrJobStatus.OCR_SUCCESS;
        };
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void failJob(OcrJob job, String message) {
        Document document = documentRepository.findActiveById(job.getDocument().getId()).orElseThrow();
        job.setStatus(OcrJobStatus.OCR_FAILED);
        job.setErrorMessage(message);
        job.setCompletedAt(Instant.now());
        if (job.getStartedAt() != null) {
            job.setDurationMs(Duration.between(job.getStartedAt(), job.getCompletedAt()).toMillis());
        }
        document.setStatus(DocumentStatus.OCR_FAILED);
        ocrJobRepository.save(job);
        documentRepository.save(document);
    }

    private String runOcrmypdf(Path input, Path output, String lang) throws IOException, InterruptedException {
        List<String> cmd = new ArrayList<>();
        cmd.add("ocrmypdf");
        cmd.add("--lang");
        cmd.add(lang != null ? lang : ocrProperties.langDefault());
        cmd.add("--deskew");
        cmd.add("--rotate-pages");
        cmd.add("--output-type");
        cmd.add("pdfa");
        // PDF déjà texte (natif ou OCR) : sans ce flag, ocrmypdf sort en code 6 (PriorOcrFoundError).
        cmd.add("--skip-text");
        cmd.add(input.toAbsolutePath().toString());
        cmd.add(output.toAbsolutePath().toString());

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);
        Process p = pb.start();
        StringBuilder out = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                out.append(line).append('\n');
            }
        }
        boolean finished = p.waitFor(ocrProperties.timeoutMinutes(), TimeUnit.MINUTES);
        if (!finished) {
            p.destroyForcibly();
            throw new IOException("Timeout OCR (" + ocrProperties.timeoutMinutes() + " min)");
        }
        if (p.exitValue() != 0) {
            throw new IOException("ocrmypdf code " + p.exitValue() + ": " + out);
        }
        return out.toString();
    }

    public static String resolveOcrLang(com.archivage.common.domain.DocumentLanguage language, String defaultLang) {
        if (language == null) {
            return defaultLang;
        }
        return switch (language) {
            case FRENCH -> "fra";
            case PORTUGUESE -> "por";
            case MULTILINGUAL -> "fra+por";
            case OTHER -> defaultLang;
        };
    }
}
