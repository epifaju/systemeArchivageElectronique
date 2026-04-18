package com.archivage.storage.clamav;

import com.archivage.common.exception.ApiException;
import org.springframework.http.HttpStatus;

public class VirusDetectedException extends ApiException {

    public VirusDetectedException(String details) {
        super(HttpStatus.UNPROCESSABLE_ENTITY, "VIRUS_DETECTED", "Fichier rejeté par l'analyse antivirus: " + details);
    }
}
