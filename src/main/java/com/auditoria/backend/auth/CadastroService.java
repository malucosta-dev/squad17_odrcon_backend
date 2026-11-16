package com.auditoria.backend.auth;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CadastroService {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final JdbcTemplate jdbcTemplate;

    public CadastroService(
            UsuarioRepository usuarioRepository,
            PasswordEncoder passwordEncoder,
            JdbcTemplate jdbcTemplate
    ) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public Usuario cadastrar(RegistroRequest request) {
        String nome = request.nome().strip();
        String email = request.email()
                .trim()
                .toLowerCase(Locale.ROOT);

        if (nome.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Informe um nome válido."
            );
        }

        // BCrypt possui um limite de 72 bytes para a senha.
        if (request.senha().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "A senha excede o limite de tamanho permitido."
            );
        }

        // Bloqueia alterações na empresa durante a criação da conta.
        var empresas = jdbcTemplate.queryForList(
                """
                SELECT id_empresa
                FROM public.empresas
                WHERE id_empresa = ?
                  AND is_ativo_empresa = TRUE
                FOR SHARE
                """,
                Integer.class,
                request.idEmpresa()
        );

        if (empresas.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Selecione uma empresa ativa."
            );
        }

        boolean emailCadastrado = Boolean.TRUE.equals(
                jdbcTemplate.queryForObject(
                        """
                        SELECT EXISTS (
                            SELECT 1
                            FROM public.usuario
                            WHERE id_empresa = ?
                              AND LOWER(TRIM(email_usuario)) = ?
                        )
                        """,
                        Boolean.class,
                        request.idEmpresa(),
                        email
                )
        );

        if (emailCadastrado) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Este e-mail já está cadastrado nesta empresa."
            );
        }

        Usuario usuario = new Usuario(
                request.idEmpresa(),
                nome,
                email,
                passwordEncoder.encode(request.senha())
        );

        return usuarioRepository.saveAndFlush(usuario);
    }
}