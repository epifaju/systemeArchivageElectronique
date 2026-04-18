CREATE TABLE ocr_jobs (
    id             BIGSERIAL PRIMARY KEY,
    document_id    BIGINT NOT NULL REFERENCES documents (id) ON DELETE CASCADE,
    status         VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    started_at     TIMESTAMPTZ,
    completed_at   TIMESTAMPTZ,
    duration_ms    BIGINT,
    ocr_engine     VARCHAR(50) DEFAULT 'tesseract',
    ocr_lang       VARCHAR(50),
    error_message  TEXT,
    log_output     TEXT,
    page_count     INT,
    retry_count    INT NOT NULL DEFAULT 0,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_ocr_jobs_status CHECK (status IN (
        'PENDING', 'PROCESSING', 'OCR_SUCCESS', 'OCR_PARTIAL', 'OCR_FAILED', 'NEEDS_REVIEW'
    ))
);

CREATE INDEX idx_ocr_jobs_document ON ocr_jobs (document_id);
CREATE INDEX idx_ocr_jobs_status ON ocr_jobs (status);
