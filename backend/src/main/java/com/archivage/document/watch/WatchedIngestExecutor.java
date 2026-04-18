package com.archivage.document.watch;

import com.archivage.document.DocumentUploadService;
import com.archivage.document.dto.DocumentDto;
import com.archivage.document.dto.UploadRequest;
import com.archivage.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Une transaction par fichier pour le dossier surveillé : un échec n’annule pas les autres imports.
 */
@Service
@RequiredArgsConstructor
public class WatchedIngestExecutor {

    private final DocumentUploadService documentUploadService;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public DocumentDto ingest(byte[] content, String originalFilename, UploadRequest request, User uploader) {
        return documentUploadService.ingestDocument(content, originalFilename, request, uploader);
    }
}
