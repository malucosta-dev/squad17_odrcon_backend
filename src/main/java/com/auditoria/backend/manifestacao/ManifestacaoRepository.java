package com.auditoria.backend.manifestacao;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ManifestacaoRepository extends JpaRepository<Manifestacao, Integer> {

    Optional<Manifestacao> findByProtocoloUnico(String protocoloUnico);
}
