package com.archivage.ocr;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TextExtractionServiceTest {

    private TextExtractionService service;

    @BeforeEach
    void setUp() {
        service = new TextExtractionService();
    }

    @Test
    void countPages_missingFile_throws() {
        Path missing = Path.of("nonexistent-path-for-test.pdf");
        assertThatThrownBy(() -> service.countPages(missing))
                .isInstanceOf(java.io.IOException.class);
    }

    @Test
    void extractPlainText_missingFile_throws() {
        Path missing = Path.of("nonexistent-extract-plain-text.pdf");
        assertThatThrownBy(() -> service.extractPlainText(missing))
                .isInstanceOf(java.io.IOException.class);
    }

    @Test
    void countPages_and_extractPlainText_onMinimalPdf(@TempDir Path temp) throws Exception {
        Path pdf = temp.resolve("one-page.pdf");
        try (PDDocument doc = new PDDocument()) {
            doc.addPage(new PDPage());
            doc.save(pdf.toFile());
        }
        assertThat(service.countPages(pdf)).isEqualTo(1);
        assertThat(service.extractPlainText(pdf)).isNotNull();
    }
}
