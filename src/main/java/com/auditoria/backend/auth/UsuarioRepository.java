
package com.auditoria.backend.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UsuarioRepository
        extends JpaRepository<Usuario, Integer> {

    Optional<Usuario> findByEmailUsuarioIgnoreCaseAndIdEmpresa(
            String emailUsuario,
            Integer idEmpresa
    );
}
