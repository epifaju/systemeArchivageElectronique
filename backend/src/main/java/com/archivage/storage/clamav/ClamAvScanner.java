package com.archivage.storage.clamav;

import com.archivage.config.AppClamavProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/**
 * Client minimal ClamAV (protocole INSTREAM sur TCP).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ClamAvScanner {

    private static final byte[] INSTREAM = "zINSTREAM\0".getBytes(StandardCharsets.US_ASCII);

    private final AppClamavProperties props;

    public boolean isEnabled() {
        return props.enabled();
    }

    /**
     * Analyse le flux ; lève {@link VirusDetectedException} si une signature est trouvée.
     */
    public void scanStream(InputStream input, long maxBytes) throws IOException {
        if (!props.enabled()) {
            return;
        }
        long transferred = 0;
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(props.host(), props.port()), props.connectTimeoutMs());
            socket.setSoTimeout(props.readTimeoutMs());
            OutputStream out = socket.getOutputStream();
            InputStream in = socket.getInputStream();
            out.write(INSTREAM);

            byte[] buf = new byte[32_768];
            int n;
            while ((n = input.read(buf)) != -1) {
                transferred += n;
                if (transferred > maxBytes) {
                    throw new IOException("Fichier trop volumineux pour le scan antivirus");
                }
                writeChunk(out, buf, n);
            }
            writeChunk(out, buf, 0);

            String response = readResponse(in);
            if (response.contains("FOUND")) {
                throw new VirusDetectedException(response.trim());
            }
            if (!response.contains("OK")) {
                log.warn("Réponse ClamAV inattendue: {}", response);
                throw new IOException("Analyse antivirus indéterminée: " + response);
            }
        }
    }

    private static void writeChunk(OutputStream out, byte[] buf, int len) throws IOException {
        ByteBuffer bb = ByteBuffer.allocate(4);
        bb.putInt(len);
        out.write(bb.array());
        if (len > 0) {
            out.write(buf, 0, len);
        }
        out.flush();
    }

    private static String readResponse(InputStream in) throws IOException {
        StringBuilder sb = new StringBuilder();
        byte[] buf = new byte[512];
        int n;
        while ((n = in.read(buf)) > 0) {
            sb.append(new String(buf, 0, n, StandardCharsets.US_ASCII));
            if (sb.toString().contains("\n")) {
                break;
            }
        }
        return sb.toString();
    }
}
