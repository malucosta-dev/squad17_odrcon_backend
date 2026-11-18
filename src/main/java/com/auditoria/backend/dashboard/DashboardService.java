package com.auditoria.backend.dashboard;

import java.util.List;
import java.util.Map;

public interface DashboardService {
    List<Map<String, Object>> reclamacoesPorMes(Integer idEmpresa, String periodo);
    List<Map<String, Object>> reclamacoesPorPrioridade(Integer idEmpresa, String periodo);
    List<Map<String, Object>> who5PorMes(Integer idEmpresa, String periodo);
    Map<String, Object> resumo(Integer idEmpresa, String periodo);
}