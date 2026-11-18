package com.auditoria.backend.ia;

import org.springframework.http.HttpStatus;

public class IaIntegracaoException extends RuntimeException {

    private final HttpStatus status;

    public IaIntegracaoException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public IaIntegracaoException(HttpStatus status, String message, Throwable cause) {
        super(message, cause);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}

