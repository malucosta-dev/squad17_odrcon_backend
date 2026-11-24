package com.auditoria.backend.ia;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Clock;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class IaControllerTest {

        private GeminiService geminiService;
        private IaRateLimiter iaRateLimiter;
        private MockMvc mockMvc;

        @BeforeEach
        void configurar() {
                geminiService = mock(GeminiService.class);
                iaRateLimiter = new IaRateLimiter(3, 10, 60, "10.0.0.1", Clock.systemUTC());
                IaController controller = new IaController(geminiService, iaRateLimiter);
                mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
        }

        @Test
        void deveReformularReclamacaoSemInstrucoesOpcionaisComSucesso() throws Exception {
                when(geminiService.reformularReclamacao(
                                "Texto cheio de raiva pelo atraso!"))
                                .thenReturn("Venho registrar minha insatisfação quanto ao atraso ocorrido.");

        String payload = """
                {
                  "textoOriginal": "Texto cheio de raiva pelo atraso!"
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
        void deveReformularReclamacaoComInstrucoesOpcionaisDoRevisor() throws Exception {
                when(geminiService.reformularReclamacao(
                                "Meu pagamento atrasou 5 dias.",
                                "Manter em primeira pessoa."))
                                .thenReturn("Informo que meu pagamento atrasou 5 dias e solicito regularização.");

                String payload = """
                                {
                                  "textoOriginal": "Meu pagamento atrasou 5 dias.",
                                  "instrucoesRevisor": "Manter em primeira pessoa."
                                }
                                """;

                mockMvc.perform(post("/api/ia/reformular")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(payload))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.textoReformulado")
                                                .value("Informo que meu pagamento atrasou 5 dias e solicito regularização."));
        }

        @Test
        void deveRetornarBadRequestQuandoInstrucoesRevisorExcederemLimite() throws Exception {
                String payload = """
                                {
                                  "textoOriginal": "Reclamação válida.",
                                  "instrucoesRevisor": "%s"
                                }
                                """.formatted("a".repeat(1001));

                mockMvc.perform(post("/api/ia/reformular")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(payload))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.erro")
                                                .value("As instruções do revisor devem ter no máximo 1000 caracteres."));

                verifyNoInteractions(geminiService);
        }

        @Test
        void deveRetornarBadRequestQuandoTextoOriginalEstiverEmBranco() throws Exception {
                String payload = """
                                {
                                  "textoOriginal": "   "
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
    void deveRetornarBadRequestQuandoTextoOriginalExcederLimite() throws Exception {
        String payload = "{\"textoOriginal\":\"%s\"}".formatted("a".repeat(5001));

        mockMvc.perform(post("/api/ia/reformular")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro")
                        .value("O texto original deve ter no máximo 5000 caracteres."));

        verifyNoInteractions(geminiService);
    }

    @Test
    void deveRetornarGatewayTimeoutQuandoApiExternaExcederTempoLimite() throws Exception {
        when(geminiService.reformularReclamacao(anyString()))
                .thenThrow(new IaIntegracaoException(
                        HttpStatus.GATEWAY_TIMEOUT,
                        "Tempo limite excedido ao comunicar com a API do Gemini."
                ));

        String payload = """
                {
                  "textoOriginal": "Reclamação sobre cobrança indevida."
                }
                """;

        mockMvc.perform(post("/api/ia/reformular")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isGatewayTimeout())
                .andExpect(jsonPath("$.erro")
                        .value("Tempo limite excedido ao comunicar com a API do Gemini."));
    }

        @Test
        void deveRetornar429ComRetryAfterQuandoSessaoExcederRateLimitSemBloquearOutroUsuarioNoMesmoIp()
                        throws Exception {
                when(geminiService.reformularReclamacao(anyString()))
                                .thenReturn("Texto reformulado com sucesso.");

                MockHttpSession sessaoFuncionarioA = new MockHttpSession();
                MockHttpSession sessaoFuncionarioB = new MockHttpSession();
                String ipCorporativoCompartilhado = "192.168.10.25";
                String payload = "{\"textoOriginal\":\"Relato de problema no setor.\"}";

                // Funcionário A atinge o limite de 3 requisições da própria sessão
                for (int i = 0; i < 3; i++) {
                        mockMvc.perform(post("/api/ia/reformular")
                                        .session(sessaoFuncionarioA)
                                        .with(req -> {
                                                req.setRemoteAddr(ipCorporativoCompartilhado);
                                                return req;
                                        })
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(payload))
                                        .andExpect(status().isOk());
                }

                // 4ª requisição do Funcionário A recebe HTTP 429 Too Many Requests com header
                // Retry-After
                mockMvc.perform(post("/api/ia/reformular")
                                .session(sessaoFuncionarioA)
                                .with(req -> {
                                        req.setRemoteAddr(ipCorporativoCompartilhado);
                                        return req;
                                })
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(payload))
                                .andExpect(status().isTooManyRequests())
                                .andExpect(header().exists("Retry-After"))
                                .andExpect(jsonPath("$.erro").exists());

                // Funcionário B, compartilhando o mesmo IP corporativo mas em outra sessão, NÃO
                // é bloqueado
                mockMvc.perform(post("/api/ia/reformular")
                                .session(sessaoFuncionarioB)
                                .with(req -> {
                                        req.setRemoteAddr(ipCorporativoCompartilhado);
                                        return req;
                                })
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(payload))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.textoReformulado").value("Texto reformulado com sucesso."));
        }

        @Test
        void deveIgnorarXForwardedForDeOrigemNaoConfiavelERespeitarDeProxyConfiavel() {
                MockHttpSession sessao = new MockHttpSession();

                org.springframework.mock.web.MockHttpServletRequest reqNaoConfiavel = new org.springframework.mock.web.MockHttpServletRequest();
                reqNaoConfiavel.setSession(sessao);
                reqNaoConfiavel.setRemoteAddr("203.0.113.50");
                reqNaoConfiavel.addHeader("X-Forwarded-For", "198.51.100.1");

                // Origem não está em trustedProxies (10.0.0.1), portanto deve ignorar o
                // X-Forwarded-For forjado
                org.junit.jupiter.api.Assertions.assertEquals(
                                "203.0.113.50",
                                iaRateLimiter.extrairIpCliente(reqNaoConfiavel));

                // Quando a requisição vem do proxy confiável (10.0.0.1), extrai o IP real
                // encaminhado
                org.springframework.mock.web.MockHttpServletRequest reqProxyConfiavel = new org.springframework.mock.web.MockHttpServletRequest();
                reqProxyConfiavel.setSession(sessao);
                reqProxyConfiavel.setRemoteAddr("10.0.0.1");
                reqProxyConfiavel.addHeader("X-Forwarded-For", "198.51.100.77, 10.0.0.1");

                org.junit.jupiter.api.Assertions.assertEquals(
                                "198.51.100.77",
                                iaRateLimiter.extrairIpCliente(reqProxyConfiavel));
        }
}
