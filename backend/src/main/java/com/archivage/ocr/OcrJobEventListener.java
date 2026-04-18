package com.archivage.ocr;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class OcrJobEventListener {

    private final OcrAsyncLauncher ocrAsyncLauncher;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onJobCreated(OcrJobCreatedEvent event) {
        ocrAsyncLauncher.launch(event.jobId());
    }
}
