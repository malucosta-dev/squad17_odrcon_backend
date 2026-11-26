package com.auditoria.backend.solucao;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface Proposta_solucaoRepository extends JpaRepository<Proposta_solucao, Integer> {
    List<Proposta_solucao> findByManifestacao_IdManifestacao(Integer idManifestacao);
}