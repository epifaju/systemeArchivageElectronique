package com.archivage.document.mapper;

import com.archivage.document.dto.DocumentDto;
import com.archivage.document.entity.Document;
import com.archivage.document.entity.DocumentTag;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

import java.util.List;
import java.util.Map;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface DocumentMapper {

    @Mapping(target = "documentTypeId", source = "document.documentType.id")
    @Mapping(target = "departmentId", source = "document.department.id")
    @Mapping(target = "documentTypeCode", source = "document.documentType.code")
    @Mapping(target = "documentTypeLabelFr", source = "document.documentType.labelFr")
    @Mapping(target = "documentTypeLabelPt", source = "document.documentType.labelPt")
    @Mapping(target = "ocrAvailable", expression = "java(document.getOcrPath() != null && !document.getOcrPath().isBlank())")
    @Mapping(target = "tags", expression = "java(mapTags(document.getTags()))")
    @Mapping(target = "customMetadata", expression = "java(emptyToNull(document.getCustomMetadata()))")
    DocumentDto toDto(Document document);

    default Map<String, Object> emptyToNull(Map<String, Object> m) {
        return m == null || m.isEmpty() ? null : m;
    }

    default List<String> mapTags(List<DocumentTag> tags) {
        if (tags == null) {
            return List.of();
        }
        return tags.stream().map(DocumentTag::getTag).toList();
    }
}
