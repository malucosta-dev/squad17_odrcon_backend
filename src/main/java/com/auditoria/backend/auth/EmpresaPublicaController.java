package com.auditoria.backend.auth;

import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class EmpresaPublicaController {

    private final JdbcTemplate jdbcTemplate;

    public EmpresaPublicaController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public record EmpresaOpcao(
            Integer idEmpresa,
            String nomeEmpresa
    ) {}

    @GetMapping("/api/empresas/publicas")
    public List<EmpresaOpcao> listarEmpresasAtivas() {
        String sql = """
            SELECT id_empresa, nome_empresa
            FROM public.empresas
            WHERE is_ativo_empresa = TRUE
            ORDER BY nome_empresa, id_empresa
            """;

        return jdbcTemplate.query(
                sql,
                (resultado, numeroLinha) -> new EmpresaOpcao(
                        resultado.getInt("id_empresa"),
                        resultado.getString("nome_empresa")
                )
        );
    }
}