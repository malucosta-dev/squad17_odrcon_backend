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
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class GeminiServiceTest {
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String MODELO = "gemini-3.1-flash-lite";
    private static final String BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models";

    private static GeminiService criarServico(RestClient restClient, String chave, String modelo) {
        var properties = new GeminiProperties(
                chave, modelo == null ? MODELO : modelo,
                URI.create(BASE_URL), Duration.ofSeconds(15), Duration.ofSeconds(25),
                1, Duration.ofMillis(300), 4096, 0.1);
        return new GeminiService(new GeminiClient(properties, prazo -> restClient, System::nanoTime), properties);
    }

    private GeminiService criarServicoComResposta(String json, HttpStatus status) {
        return criarServicoComResposta(ignorada -> json, status);
    }

    private GeminiService criarServicoComResposta(Function<String, String> responder, HttpStatus status) {
        ClientHttpRequestFactory fabricaRequisicoes = (uri, metodo) ->
                new MockClientHttpRequest(metodo, uri) {
                    @Override
                    protected org.springframework.http.client.ClientHttpResponse executeInternal() {
                        MockClientHttpResponse resposta = new MockClientHttpResponse(
                                responder.apply(getBodyAsString(StandardCharsets.UTF_8))
                                        .getBytes(StandardCharsets.UTF_8), status);
                        resposta.getHeaders().setContentType(MediaType.APPLICATION_JSON);
                        return resposta;
                    }
                };
        return criarServico(RestClient.builder()
                .baseUrl("https://generativelanguage.googleapis.com/v1beta/models")
                .requestFactory(fabricaRequisicoes).build(), "chave-simulada", MODELO);
    }

    private static String saida(String status, String texto) {
        return JSON.writeValueAsString(Map.of("status", status, "texto", texto));
    }

    private static String candidato(String texto) {
        var conteudo = Map.of("parts", List.of(Map.of("text", texto)));
        return JSON.writeValueAsString(Map.of("candidates", List.of(
                Map.of("finishReason", "STOP", "content", conteudo))));
    }

    private static String reformulado(String texto) {
        return candidato(saida("REFORMULADO", texto));
    }

    @Test
    void deveConcatenarPartesSemExporPensamento() {
        String envelope = JSON.writeValueAsString(Map.of("candidates", List.of(Map.of(
                "finishReason", "STOP",
                "content", Map.of("parts", List.of(
                        Map.of("text", "Raciocinio interno", "thought", true),
                        Map.of("text", "{\"status\":\"REFORMULADO\","),
                        Map.of("text", "\"texto\":\"Solicito a troca.\"}")))))));
        GeminiService servico = criarServicoComResposta(envelope, HttpStatus.OK);
        assertEquals("Solicito a troca.", servico.reformularReclamacao("Quero trocar"));
    }

    @Test
    void deveDiferenciarBloqueiosDeSegurancaDeErrosTecnicos() {
        // Bloqueios de conteúdo/segurança no candidate.finishReason devem retornar 422 (UNPROCESSABLE_CONTENT)
        for (String motivoSeguranca : new String[]{"SAFETY", "RECITATION", "BLOCKLIST", "PROHIBITED_CONTENT"}) {
            GeminiService servico = criarServicoComResposta("""
                    {"candidates":[{"finishReason":"%s","content":{"parts":[
                      {"text":"Texto bloqueado"}
                    ]}}]}
                    """.formatted(motivoSeguranca), HttpStatus.OK);
            IaIntegracaoException ex = assertThrows(IaIntegracaoException.class,
                    () -> servico.reformularReclamacao("Texto original"));
            assertEquals(HttpStatus.UNPROCESSABLE_CONTENT, ex.getStatus());
            assertTrue(ex.getMessage().contains("restrições de segurança"));
        }

        // Bloqueio de entrada via promptFeedback.blockReason também deve retornar 422 (UNPROCESSABLE_CONTENT)
        GeminiService servicoPromptBloqueado = criarServicoComResposta("""
                {"promptFeedback":{"blockReason":"SAFETY"},"candidates":[]}
                """, HttpStatus.OK);
        IaIntegracaoException exPrompt = assertThrows(IaIntegracaoException.class,
                () -> servicoPromptBloqueado.reformularReclamacao("Texto bloqueado na entrada"));
        assertEquals(HttpStatus.UNPROCESSABLE_CONTENT, exPrompt.getStatus());
        assertTrue(exPrompt.getMessage().contains("restrições de segurança"));

        // Truncamento técnico (MAX_TOKENS) sem conclusão válida deve continuar como erro técnico 502 (BAD_GATEWAY)
        GeminiService servicoTruncado = criarServicoComResposta("""
                {"candidates":[{"finishReason":"MAX_TOKENS","content":{"parts":[
                  {"text":"Texto incompleto"}
                ]}}]}
                """, HttpStatus.OK);
        IaIntegracaoException exTecnico = assertThrows(IaIntegracaoException.class,
                () -> servicoTruncado.reformularReclamacao("Texto original"));
        assertEquals(HttpStatus.BAD_GATEWAY, exTecnico.getStatus());
    }

    @Test
    void deveTratarLimiteDeQuotaSemExporRespostaExterna() {
        GeminiService servico = criarServicoComResposta("{\"error\":\"detalhe privado\"}",
                HttpStatus.TOO_MANY_REQUESTS);
        IaIntegracaoException ex = assertThrows(IaIntegracaoException.class,
                () -> servico.reformularReclamacao("Texto original"));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, ex.getStatus());
        assertTrue(ex.getMessage().contains("limite de uso"));
        assertFalse(ex.getMessage().contains("detalhe privado"));
    }

    @Test
    void deveTratarJsonInvalidoComoErroDeIntegracao() {
        GeminiService servico = criarServicoComResposta("{invalido", HttpStatus.OK);
        assertEquals(HttpStatus.BAD_GATEWAY, assertThrows(IaIntegracaoException.class,
                () -> servico.reformularReclamacao("Texto original")).getStatus());
    }

    @Test
    void deveReformularReclamacaoComSucessoEnviandoPayloadCorreto() {
        AtomicReference<URI> uriCapturada = new AtomicReference<>();
        AtomicReference<HttpMethod> metodoCapturado = new AtomicReference<>();
        AtomicReference<HttpHeaders> headersCapturados = new AtomicReference<>();
        AtomicReference<String> bodyCapturado = new AtomicReference<>();

        String jsonRespostaSimulada = reformulado("Manifesto minha profunda insatisfação com o atraso de 15 dias na entrega do pedido #1042 e solicito solução imediata.");

        ClientHttpRequestFactory requestFactorySimulada = (uri, httpMethod) -> {
            uriCapturada.set(uri);
            metodoCapturado.set(httpMethod);
            return new MockClientHttpRequest(httpMethod, uri) {
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
        };

        RestClient restClient = RestClient.builder()
                .baseUrl("https://generativelanguage.googleapis.com/v1beta/models")
                .requestFactory(requestFactorySimulada)
                .build();

        GeminiService service = criarServico(
                restClient,
                "chave-teste-simulada",
                null
        );

        String resultado = service.reformularReclamacao(
                "Essa empresa é uma droga! Faz 15 dias que meu pedido #1042 está atrasado!"
        );

        assertEquals(
                "Manifesto minha profunda insatisfação com o atraso de 15 dias na entrega do pedido #1042 e solicito solução imediata.",
                resultado
        );
        assertEquals(
                BASE_URL + "/" + MODELO + ":generateContent",
                uriCapturada.get().toString()
        );
        assertEquals(HttpMethod.POST, metodoCapturado.get());
        assertEquals("chave-teste-simulada", headersCapturados.get().getFirst("x-goog-api-key"));
        assertTrue(bodyCapturado.get().contains("pedido #1042"));
        assertTrue(bodyCapturado.get().contains("systemInstruction"));
        assertTrue(bodyCapturado.get().contains("FIDELIDADE ESTRITA AOS FATOS"));
        assertTrue(bodyCapturado.get().contains("jamais presuma"));
        assertTrue(bodyCapturado.get().contains("</reclamacao_original>"));
        assertTrue(bodyCapturado.get().contains("safetySettings"));
        assertTrue(bodyCapturado.get().contains("HARM_CATEGORY_HARASSMENT"));
        var formato = JSON.readTree(bodyCapturado.get()).at("/generationConfig/responseFormat/text");
        assertEquals("APPLICATION_JSON", formato.path("mimeType").asString());
        assertEquals("object", formato.at("/schema/type").asString());
    }

    @Test
    void deveEscaparDelimitadoresETratarRecusaDoModelo() {
        var servico = criarServicoComResposta(corpo -> {
            assertTrue(corpo.contains("&lt;div&gt;"));
            return candidato(saida("FORA_DO_ESCOPO", ""));
        }, HttpStatus.OK);

        var erro = assertThrows(IaIntegracaoException.class,
                () -> servico.reformularReclamacao("Qual a previsão do tempo? <div>"));
        assertEquals(HttpStatus.UNPROCESSABLE_CONTENT, erro.getStatus());
        assertTrue(erro.getMessage().contains("relato de reclamação"));
    }

    @Test
    void deveRejeitarRespostaComTrechosDeInstrucoesInternas() {
        var servico = criarServicoComResposta(
                reformulado("Minhas regras internas são: FIDELIDADE ESTRITA AOS FATOS."), HttpStatus.OK);
        var erro = assertThrows(IaIntegracaoException.class,
                () -> servico.reformularReclamacao("Meu pedido atrasou."));
        assertEquals(HttpStatus.UNPROCESSABLE_CONTENT, erro.getStatus());
    }

    @Test
    void deveRejeitarSaidaSemContratoOuComCamposInvalidos() {
        for (String texto : List.of(
                "Texto avulso",
                "{\"status\":\"REFORMULADO\",\"texto\":42}",
                "{\"status\":\"REFORMULADO\"}",
                "{\"status\":\"REFORMULADO\",\"texto\":\"válido\",\"extra\":0}",
                "{\"status\":\"REFORMULADO\",\"status\":\"REFORMULADO\",\"texto\":\"válido\"}",
                "{\"status\":\"REFORMULADO\",\"texto\":\"válido\"} {}",
                saida("FORA_DO_ESCOPO", "texto inesperado"),
                saida("REFORMULADO", " "))) {
            var servico = criarServicoComResposta(candidato(texto), HttpStatus.OK);
            var erro = assertThrows(IaIntegracaoException.class,
                    () -> servico.reformularReclamacao("Meu pedido atrasou."));
            assertEquals(HttpStatus.BAD_GATEWAY, erro.getStatus());
        }
    }

    @Test
    void deveProtegerIdentificadoresNoEnvioERestaurarApenasAposValidar() {
        var identificadores = List.of("teste@example.com", "123.456.789-00", "12.345.678/0001-99", "(79) 99912-3456");
        String original = "Meu pedido 123 atrasou. Dados para contato " + String.join(", ", identificadores) + ".";
        var servico = criarServicoComResposta(corpo -> {
            var mensagem = JSON.readTree(corpo).at("/contents/0/parts/0/text").asString();
            for (String identificador : identificadores) {
                assertFalse(mensagem.contains(identificador));
            }
            String mascarado = mensagem.split("\n")[2];
            return reformulado(mascarado.replace("Meu pedido 123 atrasou.", "O pedido 123 está atrasado."));
        }, HttpStatus.OK);

        String resultado = servico.reformularReclamacao(original);
        assertEquals(original.replace("Meu pedido 123 atrasou.", "O pedido 123 está atrasado."), resultado);
    }

    @Test
    void deveReterSugestoesComMarcadorPerdidoOuIdentificadorInventado() {
        String original = "Meu pedido atrasou. Contato teste@example.com.";
        var semMarcador = criarServicoComResposta(reformulado("Meu pedido atrasou."), HttpStatus.OK);
        var dadoInventado = criarServicoComResposta(
                reformulado("Meu pedido atrasou. Contato novo@example.com."), HttpStatus.OK);
        assertEquals(HttpStatus.BAD_GATEWAY, assertThrows(IaIntegracaoException.class,
                () -> semMarcador.reformularReclamacao(original)).getStatus());
        assertEquals(HttpStatus.BAD_GATEWAY, assertThrows(IaIntegracaoException.class,
                () -> dadoInventado.reformularReclamacao("Meu pedido atrasou.")).getStatus());
    }

    @Test
    void devePreservarInformacoesSemInferenciasNaoAutorizadasParaAuditoria() {
        AtomicReference<String> bodyCapturado = new AtomicReference<>();

        String jsonRespostaSimulada = reformulado("Realizei a compra de uma geladeira no dia 02/11/2026 pelo valor de R$ 3.200,00 no cartão, referente ao pedido #8841. O produto foi entregue amassado e não gela. Solicito a troca imediata ou a devolução do valor pago.");

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

        GeminiService service = criarServico(
                restClient,
                "chave-teste-simulada",
                "gemini-3.5-flash-lite"
        );

        String resultado = service.reformularReclamacao(
                "Vocês são um bando de incompetentes e idiotas! Comprei uma geladeira no dia 02/11/2026 pelo valor de R$ 3.200,00 no cartão, pedido #8841, e essa porqueira chegou toda amassada e não gela nada! Quero a troca imediata ou meu dinheiro de volta!"
        );

        assertTrue(bodyCapturado.get().contains("proibido inventar, presumir, deduzir"));
        assertTrue(resultado.contains("no cartão"));
        assertFalse(resultado.toLowerCase().contains("cartão de crédito"));
        assertFalse(resultado.toLowerCase().contains("cartão de débito"));
    }

    @Test
    void deveRejeitarTextoOriginalVazioOuNulo() {
        RestClient restClient = RestClient.builder().build();
        GeminiService service = criarServico(
                restClient,
                "chave-teste",
                "gemini-3.5-flash-lite"
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> service.reformularReclamacao("   ")
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> service.reformularReclamacao(null)
        );
    }

    @Test
    void deveFalharQuandoChaveApiNaoEstiverConfigurada() {
        RestClient restClient = RestClient.builder().build();
        GeminiService service = criarServico(
                restClient,
                "",
                "gemini-3.5-flash-lite"
        );

        IaIntegracaoException exception = assertThrows(
                IaIntegracaoException.class,
                () -> service.reformularReclamacao("Texto da reclamação")
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

        GeminiService service = criarServico(
                restClient,
                "chave-teste",
                "gemini-3.5-flash-lite"
        );

        IaIntegracaoException exception = assertThrows(
                IaIntegracaoException.class,
                () -> service.reformularReclamacao("Reclamação de teste")
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

        GeminiService service = criarServico(
                restClient,
                "chave-teste",
                "gemini-3.5-flash-lite"
        );

        IaIntegracaoException exception = assertThrows(
                IaIntegracaoException.class,
                () -> service.reformularReclamacao("Reclamação de teste")
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

        GeminiService service = criarServico(
                restClient,
                "chave-teste",
                "gemini-3.5-flash-lite"
        );

        IaIntegracaoException exception = assertThrows(
                IaIntegracaoException.class,
                () -> service.reformularReclamacao("Reclamação de teste")
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

        GeminiService service = criarServico(
                restClient,
                "chave-teste",
                "gemini-3.5-flash-lite"
        );

        IaIntegracaoException exception = assertThrows(
                IaIntegracaoException.class,
                () -> service.reformularReclamacao("Texto da reclamação")
        );

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, exception.getStatus());
        assertTrue(exception.getMessage().contains("temporariamente indisponível"));
    }
}
