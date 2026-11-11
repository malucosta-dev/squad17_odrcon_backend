package com.auditoria.backend.ia;

import com.auditoria.backend.auth.CsrfController;
import com.auditoria.backend.auth.SecurityConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringJUnitWebConfig(IaSegurancaTest.Configuracao.class)
class IaSegurancaTest {
    @Configuration
    @EnableWebMvc
    @EnableWebSecurity
    @Import({SecurityConfig.class, IaController.class, CsrfController.class})
    static class Configuracao {
        @Bean
        GeminiService servicoGemini() {
            return mock(GeminiService.class);
        }
    }

    @Autowired WebApplicationContext contexto;
    @Autowired GeminiService servico;
    MockMvc mvc;

    @BeforeEach
    void configurar() {
        reset(servico);
        mvc = MockMvcBuilders.webAppContextSetup(contexto).apply(springSecurity()).build();
    }

    @Test
    void deveNegarAnonimoMesmoComCsrf() throws Exception {
        mvc.perform(post("/api/ia/reformular").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"textoOriginal\":\"Pedido atrasado\"}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(servico);
    }

    @Test
    void deveNegarAutenticadoSemCsrf() throws Exception {
        mvc.perform(post("/api/ia/reformular").with(user("teste").roles("COLABORADOR"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"textoOriginal\":\"Pedido atrasado\"}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(servico);
    }

    @Test
    void devePermitirAutenticadoComCsrf() throws Exception {
        when(servico.reformularReclamacao("Pedido atrasado", null)).thenReturn("Solicito uma solucao.");
        mvc.perform(post("/api/ia/reformular").with(user("teste").roles("COLABORADOR"))
                .with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"textoOriginal\":\"Pedido atrasado\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.textoReformulado").value("Solicito uma solucao."));
    }
}
