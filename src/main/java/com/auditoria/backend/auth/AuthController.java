
package com.auditoria.backend.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
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
            Set.of("ADMIN", "RESPONSAVEL", "COLABORADOR");

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

        // Valida as credenciais e a empresa do usuário
        Usuario usuario = authService.validarCredenciais(
                request.email(),
                request.senha(),
                request.idEmpresa()
        );

        // Verifica se o perfil é permitido
        String perfil = usuario.getPerfilUsuario();

        if (perfil == null || !PERFIS_PERMITIDOS.contains(perfil)) {
            throw new IllegalArgumentException("Credenciais inválidas.");
        }

        // Identifica o usuário e sua empresa
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

        // Cria ou renova a identificação da sessão HTTP
        HttpSession sessao = httpRequest.getSession(false);

        if (sessao == null) {
            httpRequest.getSession(true);
        } else {
            httpRequest.changeSessionId();
        }

        // Registra o usuário autenticado no Spring Security
        SecurityContext contexto =
                SecurityContextHolder.createEmptyContext();

        contexto.setAuthentication(autenticacao);
        SecurityContextHolder.setContext(contexto);

        securityContextRepository.saveContext(
                contexto,
                httpRequest,
                httpResponse
        );

        // Retorna os dados do usuário sem expor a senha
        LoginResponse resposta = new LoginResponse(
                usuario.getIdUsuario(),
                usuario.getNomeUsuario(),
                usuario.getIdEmpresa(),
                perfil
        );

        return ResponseEntity.ok(resposta);
    }

    // Retorna HTTP 401 quando as credenciais forem inválidas
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
