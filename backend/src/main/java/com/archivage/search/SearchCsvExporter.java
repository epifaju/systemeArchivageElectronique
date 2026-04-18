package com.archivage.search;

import com.archivage.document.entity.Document;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * CSV séparé par point-virgule, UTF-8 avec BOM (ouverture correcte dans Excel).
 */
final class SearchCsvExporter {

    private static final byte[] BOM = "\uFEFF".getBytes(StandardCharsets.UTF_8);

    private SearchCsvExporter() {
    }

    static byte[] toBytes(List<Document> documents) {
        ByteArrayOutputStream bout = new ByteArrayOutputStream(Math.min(64 * 1024, documents.size() * 256 + 256));
        try {
            bout.write(BOM);
            OutputStreamWriter w = new OutputStreamWriter(bout, StandardCharsets.UTF_8);
            w.append("id;uuid;titre;type_code;dossier;date_document;statut;langue;confidentialite;ref_externe;auteur;mime_type\n");
            for (Document d : documents) {
                writeField(w, String.valueOf(d.getId()));
                w.append(';');
                writeField(w, d.getUuid() != null ? d.getUuid().toString() : "");
                w.append(';');
                writeField(w, d.getTitle());
                w.append(';');
                writeField(w, d.getDocumentType() != null ? d.getDocumentType().getCode() : "");
                w.append(';');
                writeField(w, d.getFolderNumber());
                w.append(';');
                writeField(w, d.getDocumentDate() != null ? d.getDocumentDate().toString() : "");
                w.append(';');
                writeField(w, d.getStatus() != null ? d.getStatus().name() : "");
                w.append(';');
                writeField(w, d.getLanguage() != null ? d.getLanguage().name() : "");
                w.append(';');
                writeField(w, d.getConfidentialityLevel() != null ? d.getConfidentialityLevel().name() : "");
                w.append(';');
                writeField(w, d.getExternalReference());
                w.append(';');
                writeField(w, d.getAuthor());
                w.append(';');
                writeField(w, d.getMimeType());
                w.append('\n');
            }
            w.flush();
        } catch (IOException e) {
            throw new IllegalStateException("Écriture CSV", e);
        }
        return bout.toByteArray();
    }

    private static void writeField(OutputStreamWriter w, String s) throws IOException {
        String v = s == null ? "" : s;
        if (v.indexOf(';') >= 0 || v.indexOf('"') >= 0 || v.indexOf('\n') >= 0 || v.indexOf('\r') >= 0) {
            w.append('"');
            w.append(v.replace("\"", "\"\""));
            w.append('"');
        } else {
            w.append(v);
        }
    }
}
