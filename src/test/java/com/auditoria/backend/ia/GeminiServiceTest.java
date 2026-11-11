package com.auditoria.backend.ia;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequest;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.mock.http.client.MockClientHttpResponse;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class GeminiServiceTest {

    private GeminiService criarServicoComResposta(String json, HttpStatus status) {
        ClientHttpRequestFactory fabricaRequisicoes = (uri, metodo) ->
                new MockClientHttpRequest(metodo, uri) {
                    @Override
                    protected org.springframework.http.client.ClientHttpResponse executeInternal() {
                        MockClientHttpResponse resposta = new MockClientHttpResponse(
                                json.getBytes(StandardCharsets.UTF_8), status);
                        resposta.getHeaders().setContentType(MediaType.APPLICATION_JSON);
                        return resposta;
                    }
                };
        return new GeminiService(RestClient.builder()
                .baseUrl("https://generativelanguage.googleapis.com/v1beta/models")
                .requestFactory(fabricaRequisicoes).build(), "chave-simulada", "gemini-3.5-flash");
    }

    @Test
    void deveConcatenarPartesSemExporPensamento() {
        GeminiService servico = criarServicoComResposta("""
                {"candidates":[{"finishReason":"STOP","content":{"parts":[
                  {"text":"Raciocinio interno", "thought":true},
                  {"text":"Solicito "}, {"text":"a troca.", "thought":false}
                ]}}]}
                """, HttpStatus.OK);
        assertEquals("Solicito a troca.", servico.reformularReclamacao("Quero trocar", null));
    }

    @Test
    void deveRejeitarRespostasInterrompidasOuBloqueadas() {
        for (String motivo : new String[]{"MAX_TOKENS", "SAFETY", "RECITATION"}) {
            GeminiService servico = criarServicoComResposta("""
                    {"candidates":[{"finishReason":"%s","content":{"parts":[
                      {"text":"Texto incompleto"}
                    ]}}]}
                    """.formatted(motivo), HttpStatus.OK);
            assertEquals(HttpStatus.BAD_GATEWAY, assertThrows(IaIntegracaoException.class,
                    () -> servico.reformularReclamacao("Texto original", null)).getStatus());
        }
    }

    @Test
    void deveTratarLimiteDeQuotaSemExporRespostaExterna() {
        GeminiService servico = criarServicoComResposta("{\"error\":\"detalhe privado\"}",
                HttpStatus.TOO_MANY_REQUESTS);
        IaIntegracaoException ex = assertThrows(IaIntegracaoException.class,
                () -> servico.reformularReclamacao("Texto original", null));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, ex.getStatus());
        assertFalse(ex.getMessage().contains("detalhe privado"));
    }

    @Test
    void deveTratarJsonInvalidoComoErroDeIntegracao() {
        GeminiService servico = criarServicoComResposta("{invalido", HttpStatus.OK);
        assertEquals(HttpStatus.BAD_GATEWAY, assertThrows(IaIntegracaoException.class,
                () -> servico.reformularReclamacao("Texto original", null)).getStatus());
    }

    @Test
    void deveReformularReclamacaoComSucessoEnviandoPayloadCorreto() {
        AtomicReference<URI> uriCapturada = new AtomicReference<>();
        AtomicReference<HttpMethod> metodoCapturado = new AtomicReference<>();
        AtomicReference<HttpHeaders> headersCapturados = new AtomicReference<>();
        AtomicReference<String> bodyCapturado = new AtomicReference<>();

        String jsonRespostaSimulada = """
                {
                  "candidates": [
                    {
                      "content": {
                        "parts": [
                          {
                            "text": "Manifesto minha profunda insatisfação com o atraso de 15 dias na entrega do pedido #1042 e solicito solução imediata."
                          }
                        ],
                        "role": "model"
                      },
                      "finishReason": "STOP"
                    }
                  ]
                }
                """;

        ClientHttpRequestFactory requestFactorySimulada = (uri, httpMethod) -> {
            uriCapturada.set(uri);
            metodoCapturado.set(httpMethod);
            MockClientHttpRequest request = new MockClientHttpRequest(httpMethod, uri) {
                @Override
                protected org.springframework.http.client.ClientHttpResponse executeInternal() throws IOException {
                    headersCapturados.set(getHeaders());
                    bodyCapturado.set(getBodyAsString(StandardCharsets.UTF_8));
                    MockClientHttpResponse response = new MockClientHttpResponse(
                            jsonRespostaSimulada.getBytes(StandardCharsets.UTF_8),
                            HttpStatus.OK
                    );
                    response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
                    return response;
                }
            };
            return request;
        };

        RestClient restClient = RestClient.builder()
                .baseUrl("https://generativelanguage.googleapis.com/v1beta/models")
                .requestFactory(requestFactorySimulada)
                .build();

        GeminiService service = new GeminiService(
                restClient,
                "chave-teste-simulada",
                "gemini-3.5-flash"
        );

        String resultado = service.reformularReclamacao(
                "Essa empresa é uma droga! Faz 15 dias que meu pedido #1042 está atrasado!",
                "Manter o número do pedido e adotar tom formal."
        );

        assertEquals(
                "Manifesto minha profunda insatisfação com o atraso de 15 dias na entrega do pedido #1042 e solicito solução imediata.",
                resultado
        );
        assertEquals(
                "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent",
                uriCapturada.get().toString()
        );
        assertEquals(HttpMethod.POST, metodoCapturado.get());
        assertEquals("chave-teste-simulada", headersCapturados.get().getFirst("x-goog-api-key"));
        assertTrue(bodyCapturado.get().contains("pedido #1042"));
        assertTrue(bodyCapturado.get().contains("Manter o número do pedido e adotar tom formal."));
        assertTrue(bodyCapturado.get().contains("systemInstruction"));
        assertTrue(bodyCapturado.get().contains("FIDELIDADE ESTRITA AOS FATOS"));
        assertTrue(bodyCapturado.get().contains("jamais presuma"));
    }

    @Test
    void devePreservarInformacoesSemInferenciasNaoAutorizadasParaAuditoria() {
        AtomicReference<String> bodyCapturado = new AtomicReference<>();

        String jsonRespostaSimulada = """
                {
                  "candidates": [
                    {
                      "content": {
                        "parts": [
                          {
                            "text": "Realizei a compra de uma geladeira no dia 02/11/2026 pelo valor de R$ 3.200,00 no cartão, referente ao pedido #8841. O produto foi entregue amassado e não gela. Solicito a troca imediata ou a devolução do valor pago."
                          }
                        ],
                        "role": "model"
                      },
                      "finishReason": "STOP"
                    }
                  ]
                }
                """;

        ClientHttpRequestFactory requestFactorySimulada = (uri, httpMethod) ->
                new MockClientHttpRequest(httpMethod, uri) {
                    @Override
                    protected org.springframework.http.client.ClientHttpResponse executeInternal() throws IOException {
                        bodyCapturado.set(getBodyAsString(StandardCharsets.UTF_8));
                        MockClientHttpResponse response = new MockClientHttpResponse(
                                jsonRespostaSimulada.getBytes(StandardCharsets.UTF_8),
                                HttpStatus.OK
                        );
                        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
                        return response;
                    }
                };

        RestClient restClient = RestClient.builder()
                .baseUrl("https://generativelanguage.googleapis.com/v1beta/models")
                .requestFactory(requestFactorySimulada)
                .build();

        GeminiService service = new GeminiService(
                restClient,
                "chave-teste-simulada",
                "gemini-3.5-flash"
        );

        String resultado = service.reformularReclamacao(
                "Vocês são um bando de incompetentes e idiotas! Comprei uma geladeira no dia 02/11/2026 pelo valor de R$ 3.200,00 no cartão, pedido #8841, e essa porqueira chegou toda amassada e não gela nada! Quero a troca imediata ou meu dinheiro de volta!",
                "Remover xingamentos e manter tom formal."
        );

        assertTrue(bodyCapturado.get().contains("proibido inventar, presumir, deduzir"));
        assertTrue(resultado.contains("no cartão"));
        assertFalse(resultado.toLowerCase().contains("cartão de crédito"));
        assertFalse(resultado.toLowerCase().contains("cartão de débito"));
    }

    @Test
    void deveRejeitarTextoOriginalVazioOuNulo() {
        RestClient restClient = RestClient.builder().build();
        GeminiService service = new GeminiService(
                restClient,
                "chave-teste",
                "gemini-3.5-flash"
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> service.reformularReclamacao("   ", "Instrução")
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> service.reformularReclamacao(null, null)
        );
    }

    @Test
    void deveFalharQuandoChaveApiNaoEstiverConfigurada() {
        RestClient restClient = RestClient.builder().build();
        GeminiService service = new GeminiService(
                restClient,
                "",
                "gemini-3.5-flash"
        );

        IaIntegracaoException exception = assertThrows(
                IaIntegracaoException.class,
                () -> service.reformularReclamacao("Texto da reclamação", null)
        );

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, exception.getStatus());
    }

    @Test
    void deveTratarTimeoutDaApiDoGemini() {
        ClientHttpRequestFactory timeoutFactory = new ClientHttpRequestFactory() {
            @Override
            public ClientHttpRequest createRequest(URI uri, HttpMethod httpMethod) {
                return new MockClientHttpRequest(httpMethod, uri) {
                    @Override
                    protected org.springframework.http.client.ClientHttpResponse executeInternal() throws IOException {
                        throw new ResourceAccessException(
                                "Request timed out",
                                new HttpTimeoutException("request timed out")
                        );
                    }
                };
            }
        };

        RestClient restClient = RestClient.builder()
                .baseUrl("https://generativelanguage.googleapis.com/v1beta/models")
                .requestFactory(timeoutFactory)
                .build();

        GeminiService service = new GeminiService(
                restClient,
                "chave-teste",
                "gemini-3.5-flash"
        );

        IaIntegracaoException exception = assertThrows(
                IaIntegracaoException.class,
                () -> service.reformularReclamacao("Reclamação de teste", "Formalizar")
        );

        assertEquals(HttpStatus.GATEWAY_TIMEOUT, exception.getStatus());
    }

    @Test
    void deveTratarErroHttpDaApiExterna() {
        ClientHttpRequestFactory errorFactory = (uri, httpMethod) ->
                new MockClientHttpRequest(httpMethod, uri) {
                    @Override
                    protected org.springframework.http.client.ClientHttpResponse executeInternal() {
                        MockClientHttpResponse response = new MockClientHttpResponse(
                                "{\"error\": \"unavailable\"}".getBytes(StandardCharsets.UTF_8),
                                HttpStatus.INTERNAL_SERVER_ERROR
                        );
                        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
                        return response;
                    }
                };

        RestClient restClient = RestClient.builder()
                .baseUrl("https://generativelanguage.googleapis.com/v1beta/models")
                .requestFactory(errorFactory)
                .build();

        GeminiService service = new GeminiService(
                restClient,
                "chave-teste",
                "gemini-3.5-flash"
        );

        IaIntegracaoException exception = assertThrows(
                IaIntegracaoException.class,
                () -> service.reformularReclamacao("Reclamação de teste", null)
        );

        assertEquals(HttpStatus.BAD_GATEWAY, exception.getStatus());
    }

    @Test
    void deveTratarRespostaVaziaDaApiExterna() {
        ClientHttpRequestFactory emptyResponseFactory = (uri, httpMethod) ->
                new MockClientHttpRequest(httpMethod, uri) {
                    @Override
                    protected org.springframework.http.client.ClientHttpResponse executeInternal() {
                        MockClientHttpResponse response = new MockClientHttpResponse(
                                "{\"candidates\": []}".getBytes(StandardCharsets.UTF_8),
                                HttpStatus.OK
                        );
                        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
                        return response;
                    }
                };

        RestClient restClient = RestClient.builder()
                .baseUrl("https://generativelanguage.googleapis.com/v1beta/models")
                .requestFactory(emptyResponseFactory)
                .build();

        GeminiService service = new GeminiService(
                restClient,
                "chave-teste",
                "gemini-3.5-flash"
        );

        IaIntegracaoException exception = assertThrows(
                IaIntegracaoException.class,
                () -> service.reformularReclamacao("Reclamação de teste", null)
        );

        assertEquals(HttpStatus.BAD_GATEWAY, exception.getStatus());
    }

    @Test
    void deveTratarIndisponibilidadeTemporariaDaApiExterna() {
        ClientHttpRequestFactory unavailableFactory = (uri, httpMethod) ->
                new MockClientHttpRequest(httpMethod, uri) {
                    @Override
                    protected org.springframework.http.client.ClientHttpResponse executeInternal() {
                        MockClientHttpResponse unavailable = new MockClientHttpResponse(
                                "{\"error\": \"high demand\"}".getBytes(StandardCharsets.UTF_8),
                                HttpStatus.SERVICE_UNAVAILABLE
                        );
                        unavailable.getHeaders().setContentType(MediaType.APPLICATION_JSON);
                        return unavailable;
                    }
                };

        RestClient restClient = RestClient.builder()
                .baseUrl("https://generativelanguage.googleapis.com/v1beta/models")
                .requestFactory(unavailableFactory)
                .build();

        GeminiService service = new GeminiService(
                restClient,
                "chave-teste",
                "gemini-3.5-flash"
        );

        IaIntegracaoException exception = assertThrows(
                IaIntegracaoException.class,
                () -> service.reformularReclamacao("Texto da reclamação", null)
        );

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, exception.getStatus());
        assertTrue(exception.getMessage().contains("temporariamente indisponível"));
    }
}

