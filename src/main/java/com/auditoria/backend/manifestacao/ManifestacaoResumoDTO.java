package com.auditoria.backend.manifestacao;

import java.time.LocalDateTime;

public record ManifestacaoResumoDTO(
        Integer idManifestacao,
        String protocoloUnico,
        String tipo,
        String status,
        String descricaoOriginal,
        Boolean anonimo,
        LocalDateTime criadoEm
) {
    public static ManifestacaoResumoDTO de(Manifestacao m) {
        return new ManifestacaoResumoDTO(
                m.getIdManifestacao(),
                m.getProtocoloUnico(),
                m.getTipo(),
                m.getStatus(),
                m.getDescricaoOriginal(),
                m.getAnonimo(),
                m.getCriadoEm()
        );
    }
}