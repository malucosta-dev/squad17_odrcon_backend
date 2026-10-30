
package com.auditoria.backend.auth;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.HttpStatus;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class CsrfController {

    @GetMapping("/api/auth/csrf")
    public CsrfToken obterToken(HttpServletRequest request) {

        CsrfToken token = (CsrfToken) request.getAttribute(
            CsrfToken.class.getName()
        );

        if (token == null) {
            throw new ResponseStatusException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Token CSRF indisponivel."
            );
        }

        // Força a geração do token para esta sessão.
        token.getToken();

        return token;
    }
}
