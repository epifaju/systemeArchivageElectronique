package com.archivage.document.watch;

import com.archivage.common.exception.ApiException;
import com.archivage.common.exception.DuplicateDocumentException;
import com.archivage.config.WatchedIngestProperties;
import com.archivage.document.dto.UploadRequest;
import com.archivage.user.entity.User;
import com.archivage.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.unit.DataSize;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

@Component
@RequiredArgsConstructor
@Slf4j
public class WatchedFolderIngestScheduler {

    private final WatchedIngestProperties props;
    private final WatchedIngestExecutor watchedIngestExecutor;
    private final UserRepository userRepository;

    @org.springframework.beans.factory.annotation.Value("${spring.servlet.multipart.max-file-size:50MB}")
    private DataSize maxFileSize;

    @Scheduled(fixedDelayString = "${app.ingest.watch.interval-ms:60000}", initialDelayString = "60000")
    public void poll() {
        if (!props.isEnabled()) {
            return;
        }
        Path root;
        try {
            root = Path.of(props.getDirectory()).toAbsolutePath().normalize();
        } catch (Exception e) {
            log.warn("Chemin dossier surveillé invalide: {}", props.getDirectory());
            return;
        }
        try {
            Files.createDirectories(root);
        } catch (IOException e) {
            log.warn("Impossible de créer le dossier surveillé {}: {}", root, e.getMessage());
            return;
        }
        if (!Files.isDirectory(root)) {
            log.debug("Chemin dossier surveillé invalide (pas un répertoire): {}", root);
            return;
        }
        Path processed = root.resolve("processed");
        Path failed = root.resolve("failed");
        Path duplicate = root.resolve("duplicate");
        try {
            Files.createDirectories(processed);
            Files.createDirectories(failed);
            Files.createDirectories(duplicate);
        } catch (IOException e) {
            log.warn("Impossible de créer les sous-dossiers processed/failed/duplicate sous {}: {}", root, e.getMessage());
            return;
        }

        long maxBytes = maxFileSize.toBytes();
        List<Path> files;
        try (Stream<Path> stream = Files.list(root)) {
            files = stream
                    .filter(Files::isRegularFile)
                    .filter(p -> !isIgnoredFile(p.getFileName().toString()))
                    .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                    .toList();
        } catch (IOException e) {
            log.warn("Liste du dossier surveillé impossible {}: {}", root, e.getMessage());
            return;
        }

        User uploader;
        try {
            uploader = userRepository.findById(props.getUserId())
                    .orElseThrow(() -> new IllegalStateException("Utilisateur introuvable: " + props.getUserId()));
        } catch (Exception e) {
            log.error("Dossier surveillé : utilisateur configuré introuvable (userId={}). Import annulé.",
                    props.getUserId());
            return;
        }

        LocalDate docDate = props.getDocumentDate() != null
                ? props.getDocumentDate()
                : LocalDate.now(ZoneOffset.UTC);

        List<String> tagList = props.getTags();
        List<String> tags = tagList == null || tagList.isEmpty() ? null : tagList;

        for (Path file : files) {
            String name = file.getFileName().toString();
            try {
                long size = Files.size(file);
                if (size > maxBytes) {
                    log.info("Fichier trop volumineux pour le dossier surveillé ({} octets): {}", size, name);
                    moveToSubdir(file, failed, name);
                    continue;
                }
                byte[] content = Files.readAllBytes(file);
                String title = props.getTitlePrefix() + " — " + sanitizeName(name);
                UploadRequest req = new UploadRequest(
                        title,
                        props.getDocumentTypeId(),
                        props.getFolderNumber(),
                        docDate,
                        props.getLanguage(),
                        props.getConfidentiality(),
                        props.getDepartmentId(),
                        blankToNull(props.getExternalReference()),
                        blankToNull(props.getAuthor()),
                        blankToNull(props.getNotes()),
                        tags,
                        null
                );
                watchedIngestExecutor.ingest(content, name, req, uploader);
                moveToSubdir(file, processed, name);
                log.info("Dossier surveillé : import réussi pour « {} »", name);
            } catch (DuplicateDocumentException e) {
                log.info("Dossier surveillé : doublon pour « {} » — déplacement vers duplicate/", name);
                moveToSubdir(file, duplicate, name);
            } catch (ApiException e) {
                log.warn("Dossier surveillé : échec pour « {} » ({}) — déplacement vers failed/", name, e.getMessage());
                moveToSubdir(file, failed, name);
            } catch (Exception e) {
                log.warn("Dossier surveillé : erreur pour « {} »: {}", name, e.getMessage());
                moveToSubdir(file, failed, name);
            }
        }
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static boolean isIgnoredFile(String name) {
        if (name == null || name.isEmpty()) {
            return true;
        }
        if (name.startsWith(".")) {
            return true;
        }
        String lower = name.toLowerCase();
        return lower.endsWith(".tmp") || lower.endsWith(".lock") || "thumbs.db".equals(lower);
    }

    private static String sanitizeName(String name) {
        String base = name.replace("..", "").trim();
        return base.isEmpty() ? "fichier" : (base.length() > 200 ? base.substring(0, 200) : base);
    }

    private void moveToSubdir(Path source, Path destDir, String originalName) {
        try {
            Files.createDirectories(destDir);
            Path target = uniqueTarget(destDir, originalName);
            try {
                Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
                Files.deleteIfExists(source);
            }
        } catch (IOException e) {
            log.warn("Impossible de déplacer {} vers {}: {}", source, destDir, e.getMessage());
        }
    }

    private static Path uniqueTarget(Path destDir, String originalName) {
        Path target = destDir.resolve(originalName);
        if (!Files.exists(target)) {
            return target;
        }
        String base = originalName;
        String ext = "";
        int dot = originalName.lastIndexOf('.');
        if (dot > 0) {
            base = originalName.substring(0, dot);
            ext = originalName.substring(dot);
        }
        int n = 1;
        while (Files.exists(destDir.resolve(base + "-" + n + ext))) {
            n++;
        }
        return destDir.resolve(base + "-" + n + ext);
    }
}
