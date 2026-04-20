-- Annulation explicite d’un job encore en file (Phase C — PRD file OCR).
ALTER TABLE ocr_jobs DROP CONSTRAINT IF EXISTS chk_ocr_jobs_status;
ALTER TABLE ocr_jobs ADD CONSTRAINT chk_ocr_jobs_status CHECK (status IN (
    'PENDING', 'PROCESSING', 'OCR_SUCCESS', 'OCR_PARTIAL', 'OCR_FAILED', 'NEEDS_REVIEW', 'CANCELLED'
));
