package com.auditoria.backend.manifestacao;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface ManifestacaoRepository extends JpaRepository<Manifestacao, Integer> {

    Optional<Manifestacao> findByProtocoloUnico(String protocoloUnico);

    @Modifying
    @Query(value = """
            INSERT INTO manifestacao (
                id_unidade, id_empresa, id_solicitante, id_responsavel,
                tipo, status, prioridade, descricao_original, descricao_tratada,
                dados_estruturados, prazo_resposta, nivel_escalonamento,
                avaliacao_resultado, avaliacao_nota, criado_em, atualizado_em,
                canal, protocolo_unico, is_anonimo, status_processamento, nivel_gravidade
            ) VALUES (
                :idUnidade, :idEmpresa, :idSolicitante, NULL,
                'RECLAMACAO', 'ABERTA', NULL, :descricao, NULL,
                NULL, NULL, 0, NULL, NULL, :agora, :agora,
                'WEB', :protocolo, :anonimo, 'PENDENTE', NULL
            ) ON CONFLICT (protocolo_unico) DO NOTHING
            """, nativeQuery = true)
    int inserirReclamacao(@Param("idUnidade") Integer idUnidade,
                          @Param("idEmpresa") Integer idEmpresa,
                          @Param("idSolicitante") Integer idSolicitante,
                          @Param("descricao") String descricao,
                          @Param("agora") LocalDateTime agora,
                          @Param("protocolo") String protocolo,
                          @Param("anonimo") Boolean anonimo);
}
