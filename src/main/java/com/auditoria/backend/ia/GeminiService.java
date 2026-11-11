package com.auditoria.backend.ia;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.net.SocketTimeoutException;
import java.net.http.HttpClient;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.List;
import java.util.Map;

@Service
public class GeminiService {

    private static final String SYSTEM_INSTRUCTION = """
            Você é um assistente especializado em mediação, ouvidoria e auditoria do sistema ODRCON.
            Sua tarefa é reformular reclamações de maneira clara, objetiva e respeitosa, seguindo estritamente estas regras de fidelidade documental:
            1. Remova todos os palavrões, xingamentos, ofensas pessoais, ameaças e linguagem agressiva ou inadequada.
            2. Preserve integralmente todos os fatos relatados: datas, valores, nomes, números de pedidos/protocolos, problemas ocorridos, solicitações e demais informações relevantes.
            3. FIDELIDADE ESTRITA AOS FATOS (AUDITORIA): É terminantemente proibido inventar, presumir, deduzir, especificar ou acrescentar informações que não estejam expressamente presentes na reclamação original ou nas instruções do revisor. Por exemplo, se o texto mencionar apenas "no cartão", mantenha "no cartão" (jamais presuma "cartão de crédito" ou "cartão de débito"); não adicione prazos, canais, causas ou detalhes não informados.
            4. Mantenha o sentimento legítimo de insatisfação e a urgência do reclamante, expressando-os em tom firme, educado e formal.
            5. Aplique as instruções do revisor, quando informadas, sem jamais extrapolar ou inferir dados não fornecidos.
            6. Retorne apenas o texto final reformulado em português, sem introduções, títulos, aspas ou comentários adicionais.
            """;

    private final RestClient restClient;
    private final String apiKey;
    private final String model;

    @Autowired
    public GeminiService(
            @Value("${gemini.api.key:}") String apiKey,
            @Value("${gemini.api.model:gemini-3.5-flash}") String model,
            @Value("${gemini.api.base-url:https://generativelanguage.googleapis.com/v1beta/models}") String baseUrl,
            @Value("${gemini.api.timeout-seconds:15}") long timeoutSeconds
    ) {
        Duration timeout = Duration.ofSeconds(Math.max(1, timeoutSeconds));

        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(timeout)
                .build();

        JdkClientHttpRequestFactory requestFactory =
                new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(timeout);

        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();
        this.apiKey = apiKey;
        this.model = model;
    }

    GeminiService(
            RestClient restClient,
            String apiKey,
            String model
    ) {
        this.restClient = restClient;
        this.apiKey = apiKey;
        this.model = model;
    }

    public String reformularReclamacao(
            String textoOriginal,
            String instrucoesRevisor
    ) {
        if (textoOriginal == null || textoOriginal.isBlank()) {
            throw new IllegalArgumentException(
                    "O texto original da reclamação é obrigatório."
            );
        }

        if (apiKey == null || apiKey.isBlank()) {
            throw new IaIntegracaoException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "A chave da API do Gemini (GEMINI_API_KEY) não está configurada."
            );
        }

        String promptUsuario = montarPromptUsuario(
                textoOriginal,
                instrucoesRevisor
        );

        Map<String, Object> requestBody = Map.of(
                "systemInstruction", Map.of(
                        "parts", List.of(
                                Map.of("text", SYSTEM_INSTRUCTION)
                        )
                ),
                "contents", List.of(
                        Map.of(
                                "role", "user",
                                "parts", List.of(
                                        Map.of("text", promptUsuario)
                                )
                        )
                ),
                "generationConfig", Map.of(
                        "temperature", 0.1
                )
        );

        GeminiApiResponse response;

        try {
            response = restClient.post()
                    .uri("/{model}:generateContent", model)
                    .header("x-goog-api-key", apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .body(GeminiApiResponse.class);
        } catch (ResourceAccessException ex) {
            if (isTimeout(ex)) {
                throw new IaIntegracaoException(
                        HttpStatus.GATEWAY_TIMEOUT,
                        "Tempo limite excedido ao comunicar com a API do Gemini.",
                        ex
                );
            }
            throw new IaIntegracaoException(
                    HttpStatus.BAD_GATEWAY,
                    "Falha de conexão ao comunicar com a API do Gemini.",
                    ex
            );
        } catch (RestClientResponseException ex) {
            int statusCode = ex.getStatusCode().value();
            if (statusCode == 503 || statusCode == 429) {
                throw new IaIntegracaoException(
                        HttpStatus.SERVICE_UNAVAILABLE,
                        "O serviço de IA está temporariamente indisponível devido à alta demanda. Tente novamente em instantes.",
                        ex
                );
            }
            throw new IaIntegracaoException(
                    HttpStatus.BAD_GATEWAY,
                    "A API do Gemini retornou um erro ao processar a solicitação.",
                    ex
            );
        } catch (RestClientException ex) {
            throw new IaIntegracaoException(
                    HttpStatus.BAD_GATEWAY,
                    "Erro inesperado ao comunicar com a API do Gemini.",
                    ex
            );
        }

        String textoReformulado = extrairTexto(response);

        if (textoReformulado == null || textoReformulado.isBlank()) {
            throw new IaIntegracaoException(
                    HttpStatus.BAD_GATEWAY,
                    "A API do Gemini não retornou um texto reformulado válido."
            );
        }

        return textoReformulado.trim();
    }

    private String montarPromptUsuario(
            String textoOriginal,
            String instrucoesRevisor
    ) {
        StringBuilder sb = new StringBuilder();
        sb.append("Reclamação original:\n")
          .append(textoOriginal.trim());

        if (instrucoesRevisor != null && !instrucoesRevisor.isBlank()) {
            sb.append("\n\nInstruções adicionais do revisor:\n")
              .append(instrucoesRevisor.trim());
        }

        return sb.toString();
    }

    private String extrairTexto(GeminiApiResponse response) {
        if (response == null || response.candidates() == null) {
            return null;
        }

        for (Candidate candidate : response.candidates()) {
            // Nunca devolve uma resposta interrompida como se estivesse completa.
            if (candidate != null && candidate.finishReason() != null
                    && !"STOP".equals(candidate.finishReason())) {
                continue;
            }
            if (candidate != null
                    && candidate.content() != null
                    && candidate.content().parts() != null) {
                StringBuilder texto = new StringBuilder();
                for (Part part : candidate.content().parts()) {
                    if (part != null
                            && !Boolean.TRUE.equals(part.thought())
                            && part.text() != null) {
                        texto.append(part.text());
                    }
                }
                if (!texto.toString().isBlank()) {
                    return texto.toString();
                }
            }
        }

        return null;
    }

    private boolean isTimeout(Throwable ex) {
        Throwable current = ex;
        while (current != null) {
            if (current instanceof HttpTimeoutException
                    || current instanceof SocketTimeoutException) {
                return true;
            }
            String message = current.getMessage();
            if (message != null
                    && message.toLowerCase().contains("timed out")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    record GeminiApiResponse(List<Candidate> candidates) {}

    record Candidate(Content content, String finishReason) {}

    record Content(List<Part> parts) {}

    record Part(String text, Boolean thought) {}
}
