package com.archivage.auth;

import org.springframework.security.core.AuthenticationException;

public class JwtValidationException extends AuthenticationException {

    public JwtValidationException(String msg, Throwable cause) {
        super(msg, cause);
    }
}
