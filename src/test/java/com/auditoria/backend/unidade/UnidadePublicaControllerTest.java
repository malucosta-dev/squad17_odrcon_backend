package com.auditoria.backend.unidade;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Somente leitura. Usa os dados de teste do banco: empresa 3 tem a unidade 1; empresa 1 nao tem unidades. */
@SpringBootTest
@AutoConfigureMockMvc
class UnidadePublicaControllerTest {

    @Autowired
    MockMvc mvc;

    @Test
    void listaUnidadesDaEmpresa() throws Exception {
        mvc.perform(get("/api/empresas/3/unidades"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].idUnidade").value(1))
                .andExpect(jsonPath("$[0].nomeUnidade").value("[TESTE] Unidade Central"));
    }

    @Test
    void empresaSemUnidadesDevolveListaVazia() throws Exception {
        mvc.perform(get("/api/empresas/1/unidades"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void empresaInexistenteDevolveListaVazia() throws Exception {
        mvc.perform(get("/api/empresas/999999/unidades"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void listaDeEmpresasJaExistenteContemAsEmpresasDeTeste() throws Exception {
        mvc.perform(get("/api/empresas/publicas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.idEmpresa==3)]").isNotEmpty())
                .andExpect(jsonPath("$[?(@.idEmpresa==1)]").isNotEmpty());
    }
}
