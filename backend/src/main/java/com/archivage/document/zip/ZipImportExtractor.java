package com.archivage.document.zip;

import com.archivage.common.exception.ApiException;
import org.springframework.http.HttpStatus;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Extraction ZIP « safe » : pas de Zip Slip, limite d’entrées et de taille par fichier.
 */
public final class ZipImportExtractor {

    public static final int MAX_ENTRIES = 500;
    public static final long MAX_ENTRY_UNCOMPRESSED_BYTES = 50L * 1024 * 1024;

    public record ExtractedEntry(String entryName, byte[] content) {
    }

    private ZipImportExtractor() {
    }

    public static List<ExtractedEntry> extract(byte[] zipBytes) {
        if (zipBytes == null || zipBytes.length < 4) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "ZIP_INVALID", "Archive ZIP invalide ou vide");
        }
        if (zipBytes[0] != 'P' || zipBytes[1] != 'K') {
            throw new ApiException(HttpStatus.BAD_REQUEST, "ZIP_INVALID", "Le fichier n’est pas une archive ZIP valide");
        }
        List<ExtractedEntry> out = new ArrayList<>();
        int entries = 0;
        try (ZipInputStream zis = new ZipInputStream(new java.io.ByteArrayInputStream(zipBytes), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                entries++;
                if (entries > MAX_ENTRIES) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "ZIP_TOO_MANY_ENTRIES", "Trop de fichiers dans l’archive (max " + MAX_ENTRIES + ")");
                }
                if (entry.isDirectory()) {
                    continue;
                }
                String name = entry.getName();
                if (name == null || name.isBlank()) {
                    continue;
                }
                if (name.contains("..") || name.startsWith("/") || name.startsWith("\\")) {
                    continue;
                }
                String lower = name.toLowerCase();
                if (lower.startsWith("__macosx/") || lower.endsWith(".ds_store") || lower.endsWith("thumbs.db")) {
                    continue;
                }
                String baseName = lastSegment(name);
                if (baseName.isEmpty()) {
                    continue;
                }
                long size = entry.getSize();
                if (size > 0 && size > MAX_ENTRY_UNCOMPRESSED_BYTES) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "ZIP_ENTRY_TOO_LARGE", "Fichier trop volumineux dans l’archive: " + baseName);
                }
                ByteArrayOutputStream bout = new ByteArrayOutputStream(size > 0 ? (int) Math.min(size, Integer.MAX_VALUE - 8) : 8192);
                byte[] buf = new byte[8192];
                long total = 0;
                int n;
                while ((n = zis.read(buf)) != -1) {
                    total += n;
                    if (total > MAX_ENTRY_UNCOMPRESSED_BYTES) {
                        throw new ApiException(HttpStatus.BAD_REQUEST, "ZIP_ENTRY_TOO_LARGE", "Fichier trop volumineux dans l’archive: " + baseName);
                    }
                    bout.write(buf, 0, n);
                }
                out.add(new ExtractedEntry(baseName, bout.toByteArray()));
            }
        } catch (IOException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "ZIP_READ_ERROR", "Lecture de l’archive impossible: " + e.getMessage());
        }
        if (out.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "ZIP_EMPTY", "Aucun fichier importable dans l’archive");
        }
        return out;
    }

    private static String lastSegment(String name) {
        int i = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        return i >= 0 ? name.substring(i + 1) : name;
    }
}
