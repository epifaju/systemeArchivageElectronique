package com.archivage.metadata;

import com.archivage.metadata.dto.DocumentTypeOptionDto;
import com.archivage.metadata.entity.DocumentTypeEntity;
import com.archivage.metadata.repository.DocumentTypeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/metadata")
@RequiredArgsConstructor
public class MetadataController {

    private final DocumentTypeRepository documentTypeRepository;

    @GetMapping("/document-types")
    @PreAuthorize("isAuthenticated()")
    public List<DocumentTypeOptionDto> listActiveDocumentTypes() {
        return documentTypeRepository.findByActiveTrueOrderByCodeAsc().stream()
                .map(this::toOption)
                .toList();
    }

    private DocumentTypeOptionDto toOption(DocumentTypeEntity e) {
        return new DocumentTypeOptionDto(e.getId(), e.getCode(), e.getLabelFr(), e.getLabelPt(), e.getCustomFieldsSchema());
    }
}
