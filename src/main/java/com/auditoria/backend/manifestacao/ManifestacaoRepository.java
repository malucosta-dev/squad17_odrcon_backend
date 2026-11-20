package com.auditoria.backend.manifestacao;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ManifestacaoRepository extends JpaRepository<Manifestacao, Integer> {

    Optional<Manifestacao> findByProtocoloUnico(String protocoloUnico);

    // Busca uma manifestação pelo id, garantindo que seja do tipo pedido (ex.: RECLAMACAO).
    Optional<Manifestacao> findByIdManifestacaoAndTipoIgnoreCase(Integer idManifestacao, String tipo);

    // Listagens por tipo, da mais recente para a mais antiga.
    List<Manifestacao> findByTipoIgnoreCaseOrderByCriadoEmDesc(String tipo);

    List<Manifestacao> findByTipoIgnoreCaseAndIdEmpresaOrderByCriadoEmDesc(String tipo, Integer idEmpresa);

    List<Manifestacao> findByTipoIgnoreCaseAndStatusIgnoreCaseOrderByCriadoEmDesc(String tipo, String status);

    List<Manifestacao> findByTipoIgnoreCaseAndIdEmpresaAndStatusIgnoreCaseOrderByCriadoEmDesc(
            String tipo, Integer idEmpresa, String status);
}
