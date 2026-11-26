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
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringJUnitWebConfig(IaSegurancaTest.Configuracao.class)
class IaSegurancaTest {
    @Configuration
    @EnableWebMvc
    @EnableWebSecurity
    @Import({SecurityConfig.class, IaController.class, IaRateLimiter.class, CsrfController.class})
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
    void devePermitirVisitanteComCsrf() throws Exception {
        when(servico.reformularReclamacao("Pedido atrasado")).thenReturn("Solicito uma solucao.");
        mvc.perform(post("/api/ia/reformular").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"textoOriginal\":\"Pedido atrasado\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.textoReformulado").value("Solicito uma solucao."));
    }

    @Test
    void deveNegarVisitanteSemCsrf() throws Exception {
        mvc.perform(post("/api/ia/reformular")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"textoOriginal\":\"Pedido atrasado\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("IA_CSRF_INVALIDO"));
        verifyNoInteractions(servico);
    }

    @Test
    void deveNegarVisitanteNaRotaPrivada() throws Exception {
        mvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(servico);
    }

    @Test
    void deveFornecerTokenCsrfAoVisitante() throws Exception {
        mvc.perform(get("/api/auth/csrf"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty());
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
        when(servico.reformularReclamacao("Pedido atrasado")).thenReturn("Solicito uma solucao.");
        mvc.perform(post("/api/ia/reformular").with(user("teste").roles("COLABORADOR"))
                .with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"textoOriginal\":\"Pedido atrasado\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.textoReformulado").value("Solicito uma solucao."));
    }

    @Test
    void deveAplicarRateLimitPorSessaoNoEndpointProtegidoPorCsrfSemBloquearOutraSessaoNoMesmoIp() throws Exception {
        when(servico.reformularReclamacao("Relato de teste")).thenReturn("Relato reformulado.");

        MockHttpSession sessaoAnonima1 = new MockHttpSession();
        MockHttpSession sessaoAnonima2 = new MockHttpSession();
        String ipCompartilhado = "10.20.30.40";

        for (int i = 0; i < IaRateLimiter.DEFAULT_SESSION_MAX_REQUESTS; i++) {
            mvc.perform(post("/api/ia/reformular")
                            .session(sessaoAnonima1)
                            .with(csrf())
                            .with(req -> {
                                req.setRemoteAddr(ipCompartilhado);
                                return req;
                            })
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"textoOriginal\":\"Relato de teste\"}"))
                    .andExpect(status().isOk());
        }

        // A 11ª chamada na mesma sessão anônima recebe 429 Too Many Requests com Retry-After
        mvc.perform(post("/api/ia/reformular")
                        .session(sessaoAnonima1)
                        .with(csrf())
                        .with(req -> {
                            req.setRemoteAddr(ipCompartilhado);
                            return req;
                        })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"textoOriginal\":\"Relato de teste\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.erro").exists());

        // Outro funcionário anônimo no mesmo IP corporativo (sessaoAnonima2) continua sendo atendido
        mvc.perform(post("/api/ia/reformular")
                        .session(sessaoAnonima2)
                        .with(csrf())
                        .with(req -> {
                            req.setRemoteAddr(ipCompartilhado);
                            return req;
                        })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"textoOriginal\":\"Relato de teste\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.textoReformulado").value("Relato reformulado."));
    }
}
