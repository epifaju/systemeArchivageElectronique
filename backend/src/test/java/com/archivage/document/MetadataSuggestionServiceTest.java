package com.archivage.document;

import com.archivage.document.dto.MetadataSuggestionsDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MetadataSuggestionServiceTest {

    private MetadataSuggestionService service;

    @BeforeEach
    void setUp() {
        service = new MetadataSuggestionService();
    }

    @Test
    void suggestFromOcrText_blank_returnsEmpty() {
        MetadataSuggestionsDto dto = service.suggestFromOcrText("   ");
        assertThat(dto.isoDates()).isEmpty();
        assertThat(dto.referenceLike()).isEmpty();
        assertThat(dto.emails()).isEmpty();
    }

    @Test
    void suggestFromOcrText_null_returnsEmpty() {
        MetadataSuggestionsDto dto = service.suggestFromOcrText(null);
        assertThat(dto.isoDates()).isEmpty();
    }

    @Test
    void suggestFromOcrText_extractsIsoDateEmailRef() {
        String text = """
                Réf: ABC-2024/01
                Date 2024-03-15
                contact@example.com
                """;
        MetadataSuggestionsDto dto = service.suggestFromOcrText(text);
        assertThat(dto.isoDates()).contains("2024-03-15");
        assertThat(dto.emails()).contains("contact@example.com");
        assertThat(dto.referenceLike()).isNotEmpty();
    }
}
