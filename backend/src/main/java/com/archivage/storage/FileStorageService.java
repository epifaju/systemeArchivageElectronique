package com.archivage.storage;

import com.archivage.config.AppStorageProperties;
import lombok.RequiredArgsConstructor;
import org.apache.tika.Tika;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FileStorageService {

    private final AppStorageProperties storageProperties;
    private final Tika tika = new Tika();

    public String detectMimeType(byte[] data) {
        return tika.detect(data);
    }

    public String detectMimeType(Path path) throws IOException {
        return tika.detect(path);
    }

    public String computeSha256(InputStream inputStream) throws IOException, NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] buf = new byte[8192];
        int n;
        while ((n = inputStream.read(buf)) != -1) {
            digest.update(buf, 0, n);
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    public String computeSha256(MultipartFile file) throws IOException, NoSuchAlgorithmException {
        try (InputStream is = file.getInputStream()) {
            return computeSha256(is);
        }
    }

    public String computeSha256(byte[] data) throws NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        digest.update(data);
        return HexFormat.of().formatHex(digest.digest());
    }

    /** Stocke le fichier sous /{root}/{year}/{month}/{uuid}/{filename} et retourne le chemin absolu. */
    public String store(MultipartFile file, UUID documentUuid, String safeFileName) throws IOException {
        LocalDate now = LocalDate.now();
        Path dir = Path.of(storageProperties.rootPath())
                .resolve(String.valueOf(now.getYear()))
                .resolve(String.format("%02d", now.getMonthValue()))
                .resolve(documentUuid.toString());
        Files.createDirectories(dir);
        Path target = dir.resolve(safeFileName);
        try (InputStream is = file.getInputStream()) {
            Files.copy(is, target, StandardCopyOption.REPLACE_EXISTING);
        }
        return target.toAbsolutePath().normalize().toString();
    }

    public String store(byte[] content, UUID documentUuid, String safeFileName) throws IOException {
        LocalDate now = LocalDate.now();
        Path dir = Path.of(storageProperties.rootPath())
                .resolve(String.valueOf(now.getYear()))
                .resolve(String.format("%02d", now.getMonthValue()))
                .resolve(documentUuid.toString());
        Files.createDirectories(dir);
        Path target = dir.resolve(safeFileName);
        try (InputStream is = new ByteArrayInputStream(content)) {
            Files.copy(is, target, StandardCopyOption.REPLACE_EXISTING);
        }
        return target.toAbsolutePath().normalize().toString();
    }

    public Path resolvePath(String absolutePath) {
        return Path.of(absolutePath);
    }
}
