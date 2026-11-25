package com.auditoria.backend.ia;

import org.springframework.http.HttpStatus;

public class IaIntegracaoException extends RuntimeException {

    private final IaErro codigo;

    IaIntegracaoException(IaErro codigo) {
        super(codigo.mensagem);
        this.codigo = codigo;
    }

    IaErro getCodigo() {
        return codigo;
    }

    public HttpStatus getStatus() {
        return codigo.status;
    }
}

