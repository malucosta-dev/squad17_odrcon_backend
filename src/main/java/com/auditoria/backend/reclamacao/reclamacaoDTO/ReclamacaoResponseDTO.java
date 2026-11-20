package com.auditoria.backend.reclamacao.reclamacaoDTO;

import com.auditoria.backend.manifestacao.Manifestacao;

import java.time.LocalDateTime;

// Dados de uma reclamação que o frontend recebe ao consultar o banco.
public record ReclamacaoResponseDTO(
        Integer idManifestacao,
        String protocoloUnico,
        String tipo,
        String status,
        String prioridade,
        String nivelGravidade,
        String descricaoOriginal,
        String descricaoTratada,
        String canal,
        Boolean anonimo,
        Integer idEmpresa,
        Integer idUnidade,
        Integer idSolicitante,
        String statusProcessamento,
        LocalDateTime criadoEm,
        LocalDateTime atualizadoEm
) {

    public static ReclamacaoResponseDTO from(Manifestacao m) {
        boolean anonimo = Boolean.TRUE.equals(m.getAnonimo());

        return new ReclamacaoResponseDTO(
                m.getIdManifestacao(),
                m.getProtocoloUnico(),
                m.getTipo(),
                m.getStatus(),
                m.getPrioridade(),
                m.getNivelGravidade(),
                m.getDescricaoOriginal(),
                m.getDescricaoTratada(),
                m.getCanal(),
                m.getAnonimo(),
                m.getIdEmpresa(),
                m.getIdUnidade(),
                anonimo ? null : m.getIdSolicitante(), // nunca expõe quem fez a reclamação anônima
                m.getStatusProcessamento(),
                m.getCriadoEm(),
                m.getAtualizadoEm()
        );
    }
}
