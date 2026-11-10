
package com.auditoria.backend.auth;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthService(
            UsuarioRepository usuarioRepository,
            PasswordEncoder passwordEncoder
    ) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public Usuario validarCredenciais(
            String email,
            String senha,
            Integer idEmpresa
    ) {
        if (email == null || senha == null || idEmpresa == null) {
            throw new IllegalArgumentException(
                "Credenciais inválidas."
            );
        }

        Usuario usuario = usuarioRepository
            .findByEmailUsuarioIgnoreCaseAndIdEmpresa(
                email.trim(),
                idEmpresa
            )
            .orElseThrow(() ->
                new IllegalArgumentException(
                    "Credenciais inválidas."
                )
            );

        if (!Boolean.TRUE.equals(usuario.getAtivo())) {
            throw new IllegalArgumentException(
                "Credenciais inválidas."
            );
        }

        String hash = usuario.getSenhaHashUsuario();

        if (hash == null ||
            !passwordEncoder.matches(senha, hash)) {
            throw new IllegalArgumentException(
                "Credenciais inválidas."
            );
        }

        return usuario;
    }
}
