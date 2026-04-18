CREATE INDEX idx_documents_search ON documents USING GIN (search_vector);

CREATE OR REPLACE FUNCTION update_document_search_vector()
RETURNS TRIGGER AS $$
BEGIN
    NEW.search_vector :=
        setweight(to_tsvector('french', coalesce(NEW.title, '')), 'A') ||
        setweight(to_tsvector('french', coalesce(NEW.folder_number, '')), 'B') ||
        setweight(to_tsvector('french', coalesce(NEW.external_reference, '')), 'B') ||
        setweight(to_tsvector('french', coalesce(NEW.ocr_text, '')), 'C');
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_document_search_vector
    BEFORE INSERT OR UPDATE ON documents
    FOR EACH ROW
    EXECUTE PROCEDURE update_document_search_vector();
