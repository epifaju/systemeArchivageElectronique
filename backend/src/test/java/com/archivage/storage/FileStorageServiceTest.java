package com.archivage.storage;

import com.archivage.config.AppStorageProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HexFormat;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class FileStorageServiceTest {

    @Test
    void computeSha256_byteArray_matchesExpected() throws Exception {
        FileStorageService fs = new FileStorageService(new AppStorageProperties("."));
        byte[] data = "hello".getBytes();
        String hex = fs.computeSha256(data);
        assertThat(hex).isEqualTo(
                HexFormat.of().formatHex(
                        java.security.MessageDigest.getInstance("SHA-256").digest(data)
                )
        );
    }

    @Test
    void detectMimeType_pdfBytes(@TempDir Path temp) throws Exception {
        FileStorageService fs = new FileStorageService(new AppStorageProperties(temp.toString()));
        byte[] pdf = "%PDF-1.4\n".getBytes();
        assertThat(fs.detectMimeType(pdf)).containsIgnoringCase("pdf");
    }

    @Test
    void store_byteArray_writesFile(@TempDir Path temp) throws Exception {
        FileStorageService fs = new FileStorageService(new AppStorageProperties(temp.toString()));
        UUID uuid = UUID.randomUUID();
        byte[] content = "data".getBytes();
        String absolute = fs.store(content, uuid, "doc.pdf");
        Path p = Path.of(absolute);
        assertThat(Files.exists(p)).isTrue();
        assertThat(Files.readAllBytes(p)).isEqualTo(content);
    }

    @Test
    void detectMimeType_path_detectsPdf(@TempDir Path temp) throws Exception {
        Path f = temp.resolve("x.pdf");
        Files.writeString(f, "%PDF-1.4\n");
        FileStorageService fs = new FileStorageService(new AppStorageProperties(temp.toString()));
        assertThat(fs.detectMimeType(f)).containsIgnoringCase("pdf");
    }

    @Test
    void computeSha256_inputStream_matchesByteArray() throws Exception {
        FileStorageService fs = new FileStorageService(new AppStorageProperties("."));
        byte[] data = "payload".getBytes();
        String fromStream = fs.computeSha256(new ByteArrayInputStream(data));
        assertThat(fromStream).isEqualTo(fs.computeSha256(data));
    }

    @Test
    void resolvePath_roundTrip() {
        FileStorageService fs = new FileStorageService(new AppStorageProperties("."));
        Path p = Path.of("C:", "tmp", "a.pdf");
        assertThat(fs.resolvePath(p.toString())).isEqualTo(p);
    }

    @Test
    void store_multipartFile_writesFile(@TempDir Path temp) throws Exception {
        FileStorageService fs = new FileStorageService(new AppStorageProperties(temp.toString()));
        UUID uuid = UUID.randomUUID();
        var mf = new MockMultipartFile("f", "orig.pdf", "application/pdf", "bytes".getBytes());
        String absolute = fs.store(mf, uuid, "safe.pdf");
        assertThat(Files.readAllBytes(Path.of(absolute))).isEqualTo("bytes".getBytes());
    }
}
