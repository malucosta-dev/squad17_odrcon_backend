package com.auditoria.backend.anexo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AnexoRepository extends JpaRepository<Anexo, Integer> {

    List<Anexo> findByIdManifestacao(Integer idManifestacao);
}
