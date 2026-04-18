-- Documents et étiquettes

CREATE TABLE documents (
    id                      BIGSERIAL PRIMARY KEY,
    uuid                    UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    title                   VARCHAR(500) NOT NULL,
    document_type_id        BIGINT NOT NULL REFERENCES document_types (id),
    folder_number           VARCHAR(100) NOT NULL,
    document_date           DATE NOT NULL,
    archive_date            TIMESTAMPTZ,
    status                  VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    language                VARCHAR(20) NOT NULL,
    confidentiality_level   VARCHAR(20) NOT NULL DEFAULT 'INTERNAL',
    original_path           TEXT,
    ocr_path                TEXT,
    mime_type               VARCHAR(100),
    ocr_text                TEXT,
    search_vector           TSVECTOR,
    file_size               BIGINT,
    page_count              INT,
    sha256                  VARCHAR(64),
    external_reference      VARCHAR(100),
    author                  VARCHAR(200),
    notes                   TEXT,
    uploaded_by             BIGINT REFERENCES users (id),
    validated_by            BIGINT REFERENCES users (id),
    department_id           BIGINT REFERENCES departments (id),
    is_deleted              BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at              TIMESTAMPTZ,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_documents_status CHECK (status IN (
        'PENDING', 'PROCESSING', 'OCR_SUCCESS', 'OCR_PARTIAL', 'OCR_FAILED',
        'NEEDS_REVIEW', 'VALIDATED', 'ARCHIVED'
    )),
    CONSTRAINT chk_documents_confidentiality CHECK (confidentiality_level IN (
        'PUBLIC', 'INTERNAL', 'CONFIDENTIAL', 'SECRET'
    ))
);

CREATE TABLE document_tags (
    id           BIGSERIAL PRIMARY KEY,
    document_id  BIGINT NOT NULL REFERENCES documents (id) ON DELETE CASCADE,
    tag          VARCHAR(100) NOT NULL,
    UNIQUE (document_id, tag)
);

CREATE INDEX idx_documents_type ON documents (document_type_id);
CREATE INDEX idx_documents_folder ON documents (folder_number);
CREATE INDEX idx_documents_date ON documents (document_date);
CREATE INDEX idx_documents_status ON documents (status);
CREATE INDEX idx_documents_sha ON documents (sha256);
CREATE INDEX idx_documents_department ON documents (department_id);
CREATE INDEX idx_documents_uploaded_by ON documents (uploaded_by);
CREATE INDEX idx_documents_not_deleted ON documents (is_deleted) WHERE is_deleted = FALSE;
