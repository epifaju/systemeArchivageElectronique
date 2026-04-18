package com.archivage.common.exception;

import org.springframework.http.HttpStatus;

public class DuplicateDocumentException extends ApiException {

    public DuplicateDocumentException(String message) {
        super(HttpStatus.CONFLICT, "DUPLICATE_DOCUMENT", message);
    }
}
