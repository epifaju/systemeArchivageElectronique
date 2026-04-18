package com.archivage.document.dto;

import java.util.List;

/**
 * Suggestions heuristiques extraites du texte OCR (dates, références, e-mails).
 */
public record MetadataSuggestionsDto(
        List<String> isoDates,
        List<String> referenceLike,
        List<String> emails
) {
}
