package com.archivage.ocr;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OcrAsyncLauncher {

    private final OcrWorker ocrWorker;

    @Async("ocrExecutor")
    public void launch(Long jobId) {
        ocrWorker.runWithRetries(jobId);
    }
}
