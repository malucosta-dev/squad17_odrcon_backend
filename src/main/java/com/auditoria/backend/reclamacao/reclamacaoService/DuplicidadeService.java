package com.auditoria.backend.reclamacao.reclamacaoService;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.Normalizer;
import java.util.HexFormat;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class DuplicidadeService {

    // Temporário: some ao reiniciar. Trocar por uma coluna UNIQUE quando houver banco.
    private final Set<String> hashesRegistrados = ConcurrentHashMap.newKeySet();

    public void validarERegistrar(String texto) {
        String hash = gerarHash(normalizar(texto));//Limpa o texto e o transforma em hash

        // Tenta salvar o hash na lista. TRUE se nao existia, FALSE se ja
        if (!hashesRegistrados.add(hash)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Esta reclamação já foi registrada.");
        }
    }

    private String normalizar(String texto) {
        String semAcento = Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return semAcento.toLowerCase().replaceAll("[^a-z0-9\\s]", " ").replaceAll("\\s+", " ").trim();
    }

    private String gerarHash(String texto) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(texto.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}