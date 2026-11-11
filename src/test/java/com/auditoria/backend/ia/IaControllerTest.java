package com.auditoria.backend.ia;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class IaControllerTest {

    private GeminiService geminiService;
    private MockMvc mockMvc;

    @BeforeEach
    void configurar() {
        geminiService = mock(GeminiService.class);
        IaController controller = new IaController(geminiService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void deveReformularReclamacaoComSucesso() throws Exception {
        when(geminiService.reformularReclamacao(
                "Texto cheio de raiva pelo atraso!",
                "Usar linguagem formal"
        )).thenReturn("Venho registrar minha insatisfação quanto ao atraso ocorrido.");

        String payload = """
                {
                  "textoOriginal": "Texto cheio de raiva pelo atraso!",
                  "instrucoesRevisor": "Usar linguagem formal"
                }
                """;

        mockMvc.perform(post("/api/ia/reformular")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.textoReformulado")
                        .value("Venho registrar minha insatisfação quanto ao atraso ocorrido."));
    }

    @Test
    void deveRetornarBadRequestQuandoTextoOriginalEstiverEmBranco() throws Exception {
        String payload = """
                {
                  "textoOriginal": "   ",
                  "instrucoesRevisor": "Manter tom neutro"
                }
                """;

        mockMvc.perform(post("/api/ia/reformular")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").exists());

        verifyNoInteractions(geminiService);
    }

    @Test
    void deveRetornarGatewayTimeoutQuandoApiExternaExcederTempoLimite() throws Exception {
        when(geminiService.reformularReclamacao(anyString(), any()))
                .thenThrow(new IaIntegracaoException(
                        HttpStatus.GATEWAY_TIMEOUT,
                        "Tempo limite excedido ao comunicar com a API do Gemini."
                ));

        String payload = """
                {
                  "textoOriginal": "Reclamação sobre cobrança indevida.",
                  "instrucoesRevisor": "Destacar valor cobrado"
                }
                """;

        mockMvc.perform(post("/api/ia/reformular")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isGatewayTimeout())
                .andExpect(jsonPath("$.erro")
                        .value("Tempo limite excedido ao comunicar com a API do Gemini."));
    }
}

