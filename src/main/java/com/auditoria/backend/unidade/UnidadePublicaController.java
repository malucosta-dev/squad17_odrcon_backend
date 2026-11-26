package com.auditoria.backend.unidade;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Lista as unidades ativas de uma empresa, para o formulario de reclamacao
 * enviar idEmpresa/idUnidade. Par do /api/empresas/publicas (que lista as empresas).
 */
@RestController
public class UnidadePublicaController {

    private final JdbcTemplate jdbcTemplate;

    public UnidadePublicaController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public record UnidadeOpcao(Integer idUnidade, String nomeUnidade) {}

    @GetMapping("/api/empresas/{idEmpresa}/unidades")
    public List<UnidadeOpcao> listarUnidadesAtivas(@PathVariable Integer idEmpresa) {
        String sql = """
            SELECT id_unidade, nome_unidade
            FROM public.unidade
            WHERE id_empresa = ? AND is_ativo_unidade = TRUE
            ORDER BY nome_unidade, id_unidade
            """;

        return jdbcTemplate.query(
                sql,
                (resultado, numeroLinha) -> new UnidadeOpcao(
                        resultado.getInt("id_unidade"),
                        resultado.getString("nome_unidade")
                ),
                idEmpresa
        );
    }
}
