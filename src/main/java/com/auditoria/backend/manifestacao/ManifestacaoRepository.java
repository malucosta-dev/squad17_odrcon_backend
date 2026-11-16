package com.auditoria.backend.manifestacao;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ManifestacaoRepository extends JpaRepository<Manifestacao, Integer> {

    Optional<Manifestacao> findByProtocoloUnico(String protocoloUnico);
}
