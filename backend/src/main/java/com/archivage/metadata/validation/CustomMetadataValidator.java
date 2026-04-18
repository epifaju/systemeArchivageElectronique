package com.archivage.metadata.validation;

import com.archivage.common.exception.ApiException;
import com.archivage.metadata.dto.CustomFieldDefinition;
import com.archivage.metadata.dto.CustomFieldsSchemaRoot;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class CustomMetadataValidator {

    private final ObjectMapper objectMapper;

    public CustomMetadataValidator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Valide et normalise les valeurs selon le schéma du type documentaire.
     * Si le schéma est absent ou sans champs, seules des valeurs vides sont acceptées.
     */
    public Map<String, Object> validateAndNormalize(JsonNode schemaNode, Map<String, Object> raw) {
        Map<String, Object> input = raw == null ? Map.of() : raw;
        CustomFieldsSchemaRoot schema = parseSchema(schemaNode);
        if (schema == null || schema.fields() == null || schema.fields().isEmpty()) {
            if (!input.isEmpty()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "CUSTOM_METADATA_NOT_ALLOWED",
                        "Ce type documentaire ne définit pas de champs métier personnalisés");
            }
            return null;
        }
        List<CustomFieldDefinition> defs = schema.fields();
        Set<String> allowed = defs.stream().map(CustomFieldDefinition::key).collect(Collectors.toSet());
        for (String k : input.keySet()) {
            if (!allowed.contains(k)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "CUSTOM_METADATA_UNKNOWN_KEY",
                        "Champ métier inconnu pour ce type : " + k);
            }
        }
        Map<String, Object> out = new LinkedHashMap<>();
        for (CustomFieldDefinition def : defs) {
            Object val = input.get(def.key());
            boolean req = Boolean.TRUE.equals(def.required());
            if (val == null || (val instanceof String s && s.isBlank())) {
                if (req) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "CUSTOM_METADATA_REQUIRED",
                            "Champ obligatoire : " + def.key());
                }
                continue;
            }
            out.put(def.key(), coerce(def, val));
        }
        return out.isEmpty() ? null : out;
    }

    private CustomFieldsSchemaRoot parseSchema(JsonNode schemaNode) {
        if (schemaNode == null || schemaNode.isNull() || schemaNode.isMissingNode()) {
            return null;
        }
        try {
            return objectMapper.treeToValue(schemaNode, CustomFieldsSchemaRoot.class);
        } catch (Exception e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "INVALID_SCHEMA_STORED",
                    "Schéma de métadonnées invalide pour ce type documentaire");
        }
    }

    private Object coerce(CustomFieldDefinition def, Object val) {
        String t = def.type() == null ? "STRING" : def.type().trim().toUpperCase(Locale.ROOT);
        return switch (t) {
            case "STRING" -> {
                String s = Objects.toString(val, "").trim();
                if (def.maxLength() != null && s.length() > def.maxLength()) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "CUSTOM_METADATA_TOO_LONG",
                            "Valeur trop longue pour " + def.key());
                }
                yield s;
            }
            case "NUMBER" -> {
                if (val instanceof Number n) {
                    yield BigDecimal.valueOf(n.doubleValue());
                }
                if (val instanceof String s) {
                    try {
                        yield new BigDecimal(s.trim().replace(',', '.'));
                    } catch (NumberFormatException e) {
                        throw new ApiException(HttpStatus.BAD_REQUEST, "CUSTOM_METADATA_BAD_NUMBER",
                                "Nombre invalide pour " + def.key());
                    }
                }
                throw new ApiException(HttpStatus.BAD_REQUEST, "CUSTOM_METADATA_BAD_NUMBER",
                        "Nombre attendu pour " + def.key());
            }
            case "BOOLEAN" -> {
                if (val instanceof Boolean b) {
                    yield b;
                }
                if (val instanceof String s) {
                    yield Boolean.parseBoolean(s.trim());
                }
                throw new ApiException(HttpStatus.BAD_REQUEST, "CUSTOM_METADATA_BAD_BOOLEAN",
                        "Booléen attendu pour " + def.key());
            }
            case "DATE" -> {
                String s = Objects.toString(val, "").trim();
                try {
                    yield LocalDate.parse(s, DateTimeFormatter.ISO_LOCAL_DATE).toString();
                } catch (DateTimeParseException e) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "CUSTOM_METADATA_BAD_DATE",
                            "Date attendue (AAAA-MM-JJ) pour " + def.key());
                }
            }
            default -> throw new ApiException(HttpStatus.BAD_REQUEST, "CUSTOM_METADATA_BAD_TYPE",
                    "Type de champ inconnu : " + t);
        };
    }

}
