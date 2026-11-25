package com.auditoria.backend.manifestacao;

import org.springframework.stereotype.Service;

import java.security.SecureRandom;

@Service
public class ProtocoloService {

    private final SecureRandom random = new SecureRandom();

    public String gerar() {
        return Integer.toString(100_000_000 + random.nextInt(900_000_000));
    }
}
