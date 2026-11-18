package com.auditoria.backend.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final SecurityContextRepository securityContextRepository;

    private static final Set<String> PERFIS_PERMITIDOS =
            Set.of(
                    "ADMIN",
                    "RESPONSAVEL",
                    "COLABORADOR",
                    "CLIENTE"
            );

    public AuthController(
            AuthService authService,
            SecurityContextRepository securityContextRepository
    ) {
        this.authService = authService;
        this.securityContextRepository = securityContextRepository;
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse
    ) {
        Usuario usuario = authService.validarCredenciais(
                request.email(),
                request.senha(),
                request.idEmpresa()
        );

        String perfil = usuario.getPerfilUsuario();

        if (perfil == null || !PERFIS_PERMITIDOS.contains(perfil)) {
            throw new IllegalArgumentException("Credenciais inválidas.");
        }

        String principal = usuario.getIdEmpresa()
                + ":" + usuario.getIdUsuario();

        var autenticacao =
                UsernamePasswordAuthenticationToken.authenticated(
                        principal,
                        null,
                        List.of(
                                new SimpleGrantedAuthority("ROLE_" + perfil)
                        )
                );

        // Cria a sessão ou troca seu identificador após o login.
        HttpSession sessao = httpRequest.getSession(false);

        if (sessao == null) {
            httpRequest.getSession(true);
        } else {
            httpRequest.changeSessionId();
        }

        SecurityContext contexto =
                SecurityContextHolder.createEmptyContext();

        contexto.setAuthentication(autenticacao);
        SecurityContextHolder.setContext(contexto);

        securityContextRepository.saveContext(
                contexto,
                httpRequest,
                httpResponse
        );

        LoginResponse resposta = new LoginResponse(
                usuario.getIdUsuario(),
                usuario.getNomeUsuario(),
                usuario.getIdEmpresa(),
                perfil
        );

        return ResponseEntity.ok(resposta);
    }

    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> consultarSessao(
            Authentication authentication
    ) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .build();
        }

        String perfil = authentication.getAuthorities()
                .stream()
                .map(authority -> authority.getAuthority())
                .filter(authority -> authority.startsWith("ROLE_"))
                .map(authority -> authority.substring(5))
                .filter(PERFIS_PERMITIDOS::contains)
                .findFirst()
                .orElse(null);

        if (perfil == null) {
            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .build();
        }

        String[] identificacao =
                authentication.getName().split(":");

        if (identificacao.length != 2) {
            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .build();
        }

        try {
            Integer idEmpresa = Integer.valueOf(identificacao[0]);
            Integer idUsuario = Integer.valueOf(identificacao[1]);

            return ResponseEntity.ok(
                    Map.<String, Object>of(
                            "idEmpresa", idEmpresa,
                            "idUsuario", idUsuario,
                            "perfil", perfil
                    )
            );
        } catch (NumberFormatException exception) {
            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .build();
        }
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> tratarCredenciaisInvalidas(
            IllegalArgumentException exception
    ) {
        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(Map.of(
                        "erro", "Credenciais inválidas."
                ));
    }
}