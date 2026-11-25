package com.auditoria.backend.ia;

import com.auditoria.backend.auth.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.PropertySource;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Execucao opcional: chama o Google com texto ficticio; nao carrega JPA nem acessa o Neon. */
@EnabledIfEnvironmentVariable(named = "RUN_GEMINI_LIVE_TEST", matches = "true")
@SpringJUnitWebConfig(GeminiEndpointRealTest.Configuracao.class)
class GeminiEndpointRealTest {
    @Configuration
    @EnableWebMvc
    @EnableWebSecurity
    @Import({SecurityConfig.class, IaController.class, IaRateLimiter.class, GeminiService.class, GeminiClient.class})
    @PropertySource(value = "classpath:application.properties", encoding = "UTF-8")
    static class Configuracao {
    }

    @Autowired WebApplicationContext contexto;

    @Test
    void deveReformularPeloEndpointPublicoComGeminiReal() throws Exception {
        MockMvcBuilders.webAppContextSetup(contexto).apply(springSecurity()).build()
                .perform(post("/api/ia/reformular")
                        .with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"textoOriginal":"Paguei R$ 120,00 em 12/03/2026 pelo pedido fictício 123. A entrega está atrasada. Solicito o envio."}
                                """))
                .andExpect(statusEsperado(200))
                .andExpect(jsonPath("$.textoReformulado", not(blankOrNullString())))
                .andExpect(jsonPath("$.textoReformulado", containsString("123")))
                .andExpect(jsonPath("$.textoReformulado", containsString("120")))
                .andExpect(jsonPath("$.textoReformulado", anyOf(
                        containsString("12/03/2026"), containsStringIgnoringCase("12 de março de 2026"))))
                .andExpect(jsonPath("$.textoReformulado", anyOf(
                        containsStringIgnoringCase("envio"), containsStringIgnoringCase("entrega"))));
    }

    @Test
    void deveRecusarEntradaSemReclamacaoComGeminiReal() throws Exception {
        MockMvcBuilders.webAppContextSetup(contexto).apply(springSecurity()).build()
                .perform(post("/api/ia/reformular")
                        .with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"textoOriginal":"Quanto é dois mais dois?"}
                                """))
                .andExpect(statusEsperado(422))
                .andExpect(jsonPath("$.codigo").value("IA_FORA_DO_ESCOPO"))
                .andExpect(jsonPath("$.erro", containsString("relato de reclamação")))
                .andExpect(jsonPath("$.textoReformulado").doesNotExist());
    }

    private static ResultMatcher statusEsperado(int status) {
        return resultado -> assertEquals(status, resultado.getResponse().getStatus(),
                resultado.getResponse().getContentAsString());
    }
}
