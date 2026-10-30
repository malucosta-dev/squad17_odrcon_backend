
package com.auditoria.backend.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AuthServiceTest {

    private UsuarioRepository repository;
    private PasswordEncoder encoder;
    private AuthService authService;

    @BeforeEach
    void configurar() {
        repository = mock(UsuarioRepository.class);
        encoder = new BCryptPasswordEncoder(4);
        authService = new AuthService(repository, encoder);
    }

    private Usuario criarUsuario(
            boolean ativo,
            String senha
    ) {
        Usuario usuario = mock(Usuario.class);

        when(usuario.getAtivo()).thenReturn(ativo);
        when(usuario.getSenhaHashUsuario())
            .thenReturn(encoder.encode(senha));

        return usuario;
    }

    @Test
    void deveAceitarCredenciaisCorretas() {
        Usuario usuario = criarUsuario(true, "SenhaTeste123!");

        when(repository.findByEmailUsuarioIgnoreCaseAndIdEmpresa(
                "teste@empresa.com", 1))
            .thenReturn(Optional.of(usuario));

        Usuario resultado = authService.validarCredenciais(
            "teste@empresa.com",
            "SenhaTeste123!",
            1
        );

        assertSame(usuario, resultado);
    }

    @Test
    void deveRejeitarSenhaIncorreta() {
        Usuario usuario = criarUsuario(true, "SenhaCorreta123!");

        when(repository.findByEmailUsuarioIgnoreCaseAndIdEmpresa(
                "teste@empresa.com", 1))
            .thenReturn(Optional.of(usuario));

        assertThrows(
            IllegalArgumentException.class,
            () -> authService.validarCredenciais(
                "teste@empresa.com",
                "SenhaErrada",
                1
            )
        );
    }

    @Test
    void deveRejeitarUsuarioInativo() {
        Usuario usuario = criarUsuario(false, "SenhaTeste123!");

        when(repository.findByEmailUsuarioIgnoreCaseAndIdEmpresa(
                "teste@empresa.com", 1))
            .thenReturn(Optional.of(usuario));

        assertThrows(
            IllegalArgumentException.class,
            () -> authService.validarCredenciais(
                "teste@empresa.com",
                "SenhaTeste123!",
                1
            )
        );
    }

    @Test
    void deveRejeitarUsuarioDeOutraEmpresa() {
        when(repository.findByEmailUsuarioIgnoreCaseAndIdEmpresa(
                "teste@empresa.com", 2))
            .thenReturn(Optional.empty());

        assertThrows(
            IllegalArgumentException.class,
            () -> authService.validarCredenciais(
                "teste@empresa.com",
                "SenhaTeste123!",
                2
            )
        );
    }
}
