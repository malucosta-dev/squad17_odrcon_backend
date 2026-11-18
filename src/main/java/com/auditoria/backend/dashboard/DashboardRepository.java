package com.auditoria.backend.dashboard;

import com.auditoria.backend.manifestacao.Manifestacao; // ajuste se o pacote for outro
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface DashboardRepository extends Repository<Manifestacao, Integer> {

    @Query("SELECT COUNT(m) FROM Manifestacao m " +
            "WHERE m.idEmpresa = :emp AND m.criadoEm >= :ini")
    long total(@Param("emp") Integer emp, @Param("ini") LocalDateTime ini);

    @Query("SELECT COUNT(m) FROM Manifestacao m " +
            "WHERE m.idEmpresa = :emp AND m.criadoEm >= :ini AND UPPER(m.status) = :status")
    long totalPorStatus(@Param("emp") Integer emp, @Param("ini") LocalDateTime ini,
                        @Param("status") String status);

    @Query("SELECT EXTRACT(YEAR FROM m.criadoEm), EXTRACT(MONTH FROM m.criadoEm), COUNT(m) " +
            "FROM Manifestacao m WHERE m.idEmpresa = :emp AND m.criadoEm >= :ini " +
            "GROUP BY EXTRACT(YEAR FROM m.criadoEm), EXTRACT(MONTH FROM m.criadoEm) " +
            "ORDER BY EXTRACT(YEAR FROM m.criadoEm), EXTRACT(MONTH FROM m.criadoEm)")
    List<Object[]> contarPorMes(@Param("emp") Integer emp, @Param("ini") LocalDateTime ini);

    @Query("SELECT m.prioridade, COUNT(m) FROM Manifestacao m " +
            "WHERE m.idEmpresa = :emp AND m.criadoEm >= :ini GROUP BY m.prioridade")
    List<Object[]> contarPorPrioridade(@Param("emp") Integer emp, @Param("ini") LocalDateTime ini);

    @Query("SELECT EXTRACT(YEAR FROM m.criadoEm), EXTRACT(MONTH FROM m.criadoEm), AVG(m.avaliacaoNota) " +
            "FROM Manifestacao m WHERE m.idEmpresa = :emp AND m.criadoEm >= :ini " +
            "AND m.avaliacaoNota IS NOT NULL " +
            "GROUP BY EXTRACT(YEAR FROM m.criadoEm), EXTRACT(MONTH FROM m.criadoEm) " +
            "ORDER BY EXTRACT(YEAR FROM m.criadoEm), EXTRACT(MONTH FROM m.criadoEm)")
    List<Object[]> notaPorMes(@Param("emp") Integer emp, @Param("ini") LocalDateTime ini);

    @Query("SELECT AVG(m.avaliacaoNota) FROM Manifestacao m " +
            "WHERE m.idEmpresa = :emp AND m.criadoEm >= :ini AND m.avaliacaoNota IS NOT NULL")
    Double notaMedia(@Param("emp") Integer emp, @Param("ini") LocalDateTime ini);
}