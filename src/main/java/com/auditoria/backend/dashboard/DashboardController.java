package com.auditoria.backend.dashboard;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class DashboardController {

    private final DashboardService service;

    // Só tem valor no perfil dev (application-dev.properties).
    // Sem a propriedade, fica null e o endpoint exige login.
    @Value("${dashboard.dev-empresa-id:#{null}}")
    private Integer devEmpresaId;

    public DashboardController(DashboardService service) {
        this.service = service;
    }

    @GetMapping("/reclamacoes-por-mes")
    public List<Map<String, Object>> reclamacoesPorMes(
            Authentication auth, @RequestParam(defaultValue = "todo") String periodo) {
        return service.reclamacoesPorMes(idEmpresa(auth), periodo);
    }

    @GetMapping("/reclamacoes-por-prioridade")
    public List<Map<String, Object>> reclamacoesPorPrioridade(
            Authentication auth, @RequestParam(defaultValue = "todo") String periodo) {
        return service.reclamacoesPorPrioridade(idEmpresa(auth), periodo);
    }

    @GetMapping("/who5-por-mes")
    public List<Map<String, Object>> who5PorMes(
            Authentication auth, @RequestParam(defaultValue = "todo") String periodo) {
        return service.who5PorMes(idEmpresa(auth), periodo);
    }

    @GetMapping("/resumo")
    public Map<String, Object> resumo(
            Authentication auth, @RequestParam(defaultValue = "todo") String periodo) {
        return service.resumo(idEmpresa(auth), periodo);
    }

    private Integer idEmpresa(Authentication auth) {
        boolean semLogin = auth == null || auth instanceof AnonymousAuthenticationToken;

        if (semLogin) {
            if (devEmpresaId != null) return devEmpresaId;
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }

        boolean admin = auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        if (!admin) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }

        // principal no formato "idEmpresa:idUsuario"
        return Integer.parseInt(auth.getName().split(":")[0]);
    }
}