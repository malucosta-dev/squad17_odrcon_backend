package com.auditoria.backend.ia;

import com.auditoria.backend.auth.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import static org.hamcrest.Matchers.*;
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
    @Import({SecurityConfig.class, IaController.class})
    static class Configuracao {
        @Bean
        GeminiService servicoGemini() {
            return new GeminiService(System.getenv("GEMINI_API_KEY"),
                    "gemini-3.5-flash",
                    "https://generativelanguage.googleapis.com/v1beta/models", 15);
        }
    }

    @Autowired WebApplicationContext contexto;

    @Test
    void deveReformularPeloEndpointProtegidoComGeminiReal() throws Exception {
        MockMvcBuilders.webAppContextSetup(contexto).apply(springSecurity()).build()
                .perform(post("/api/ia/reformular")
                        .with(user("teste-simulado").roles("COLABORADOR"))
                        .with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"textoOriginal":"O pedido ficticio 123 atrasou dois dias. Solicito uma solucao.",
                                 "instrucoesRevisor":"Manter os fatos e usar linguagem formal."}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.textoReformulado", not(blankOrNullString())))
                .andExpect(jsonPath("$.textoReformulado", containsString("123")));
    }
}
