package com.auditoria.backend.dashboard;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
@Transactional(readOnly = true)
public class DashboardServiceDb implements DashboardService {

    private static final String[] MESES =
            {"Jan","Fev","Mar","Abr","Mai","Jun","Jul","Ago","Set","Out","Nov","Dez"};
    private static final LocalDateTime INICIO_TUDO = LocalDateTime.of(1970, 1, 1, 0, 0);

    private final DashboardRepository repo;
    private final String statusResolvida;

    public DashboardServiceDb(DashboardRepository repo,
                              @Value("${dashboard.status-resolvida:RESOLVIDO}") String statusResolvida) {
        this.repo = repo;
        this.statusResolvida = statusResolvida.toUpperCase();
    }

    // todo -> sem limite; mes -> 1 mês atrás; trimestre -> 3 meses atrás
    private LocalDateTime inicio(String periodo) {
        if (periodo == null) return INICIO_TUDO;
        return switch (periodo.toLowerCase()) {
            case "mes" -> LocalDateTime.now().minusMonths(1);
            case "trimestre" -> LocalDateTime.now().minusMonths(3);
            default -> INICIO_TUDO;
        };
    }

    private boolean variosAnos(List<Object[]> linhas) {
        return linhas.stream().map(r -> ((Number) r[0]).intValue()).distinct().count() > 1;
    }

    private String rotulo(Object[] r, boolean anos) {
        String mes = MESES[((Number) r[1]).intValue() - 1];
        return anos ? mes + "/" + (((Number) r[0]).intValue() % 100) : mes;
    }

    @Override
    public List<Map<String, Object>> reclamacoesPorMes(Integer emp, String periodo) {
        List<Object[]> linhas = repo.contarPorMes(emp, inicio(periodo));
        boolean anos = variosAnos(linhas);
        return linhas.stream().map(r -> Map.<String, Object>of(
                "mes", rotulo(r, anos),
                "total", ((Number) r[2]).longValue())).toList();
    }

    @Override
    public List<Map<String, Object>> reclamacoesPorPrioridade(Integer emp, String periodo) {
        return repo.contarPorPrioridade(emp, inicio(periodo)).stream().map(r -> Map.<String, Object>of(
                "prioridade", r[0] == null ? "Não informada" : r[0].toString(),
                "total", ((Number) r[1]).longValue())).toList();
    }

    @Override
    public List<Map<String, Object>> who5PorMes(Integer emp, String periodo) {
        List<Object[]> linhas = repo.notaPorMes(emp, inicio(periodo));
        boolean anos = variosAnos(linhas);
        return linhas.stream().map(r -> Map.<String, Object>of(
                "mes", rotulo(r, anos),
                "media", Math.round(((Number) r[2]).doubleValue()))).toList();
    }

    @Override
    public Map<String, Object> resumo(Integer emp, String periodo) {
        LocalDateTime ini = inicio(periodo);
        long total = repo.total(emp, ini);
        long resolvidas = repo.totalPorStatus(emp, ini, statusResolvida);
        double pct = total == 0 ? 0 : (resolvidas * 100.0) / total;
        Double media = repo.notaMedia(emp, ini);
        return Map.of(
                "totalReclamacoes", total,
                "reclamacoesResolvidas", resolvidas,
                "percentualResolvidas", Math.round(pct),
                "mediaWho5", media == null ? 0 : Math.round(media));
    }
}
