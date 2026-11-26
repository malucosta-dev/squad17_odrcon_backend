package com.auditoria.backend.ia;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import javax.net.ssl.SSLException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.LongFunction;
import java.util.function.LongSupplier;

import static com.auditoria.backend.ia.IaErro.*;

@Component
@EnableConfigurationProperties(GeminiProperties.class)
class GeminiClient {
    private static final Logger LOG = LoggerFactory.getLogger(GeminiClient.class);

    private final GeminiProperties properties;
    private final LongFunction<RestClient> clienteAtePrazo;
    private final LongSupplier nanoTime;

    @Autowired
    GeminiClient(GeminiProperties properties) {
        this(properties, criarClientes(properties), System::nanoTime);
    }

    GeminiClient(GeminiProperties properties, LongFunction<RestClient> clienteAtePrazo, LongSupplier nanoTime) {
        this.properties = properties;
        this.clienteAtePrazo = clienteAtePrazo;
        this.nanoTime = nanoTime;
    }

    private static LongFunction<RestClient> criarClientes(GeminiProperties properties) {
        var httpClient = HttpClient.newBuilder().connectTimeout(properties.timeout()).build();
        var builder = RestClient.builder().baseUrl(properties.baseUrl().toASCIIString());
        return prazo -> builder.clone().requestFactory((uri, metodo) -> {
            long timeoutMillis = TimeUnit.NANOSECONDS.toMillis(prazo - System.nanoTime());
            if (timeoutMillis <= 0) {
                throw new HttpTimeoutException("Orçamento da tentativa esgotado.");
            }
            var factory = new JdkClientHttpRequestFactory(httpClient);
            factory.setReadTimeout(Duration.ofMillis(timeoutMillis));
            return factory.createRequest(uri, metodo);
        }).build();
    }

    GeminiApiResponse gerar(Map<String, Object> corpo) {
        String chave = properties.key();
        if (chave == null || chave.isBlank() || !chave.chars().allMatch(c -> c >= 0x21 && c <= 0x7e)) {
            throw new IaIntegracaoException(GEMINI_CONFIGURACAO);
        }

        long prazo = nanoTime.getAsLong() + properties.totalTimeout().toNanos();
        for (int tentativa = 1; ; tentativa++) {
            long inicio = nanoTime.getAsLong();
            long timeoutMillis = TimeUnit.NANOSECONDS.toMillis(
                    Math.min(properties.timeout().toNanos(), prazo - inicio));
            if (timeoutMillis <= 0) {
                throw new IaIntegracaoException(GEMINI_TIMEOUT);
            }
            try {
                return enviar(corpo, chave, tentativa, inicio, inicio + TimeUnit.MILLISECONDS.toNanos(timeoutMillis));
            } catch (IaIntegracaoException erro) {
                if (!erro.getCodigo().temporario || tentativa >= properties.maxAttempts()
                        || Thread.currentThread().isInterrupted()) {
                    throw erro;
                }
                long intervaloBase = properties.initialBackoff().toMillis() << (tentativa - 1);
                long esperaMillis = ThreadLocalRandom.current().nextLong(intervaloBase, intervaloBase * 2);
                if (prazo - nanoTime.getAsLong() <= TimeUnit.MILLISECONDS.toNanos(esperaMillis + 1)) {
                    throw erro;
                }
                try {
                    Thread.sleep(esperaMillis);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    throw erro;
                }
            }
        }
    }

    private GeminiApiResponse enviar(Map<String, Object> corpo, String chave, int tentativa,
                                     long inicio, long prazoTentativa) {
        try {
            return clienteAtePrazo.apply(prazoTentativa).post()
                    .uri("/{model}:generateContent", properties.model())
                    .header("x-goog-api-key", chave)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(corpo)
                    .exchange((request, response) -> {
                        int status = response.getStatusCode().value();
                        try {
                            if (status < 200 || status >= 300) {
                                throw new IaIntegracaoException(classificarStatus(status));
                            }
                            var resultado = response.bodyTo(GeminiApiResponse.class);
                            if (nanoTime.getAsLong() - prazoTentativa >= 0) {
                                throw new IaIntegracaoException(GEMINI_TIMEOUT);
                            }
                            return resultado;
                        } catch (RestClientException ex) {
                            throw new IaIntegracaoException(classificarTransporte(ex, prazoTentativa));
                        } finally {
                            registrar(tentativa, inicio, Integer.toString(status));
                        }
                    });
        } catch (RestClientException ex) {
            registrar(tentativa, inicio, "SEM_RESPOSTA");
            throw new IaIntegracaoException(classificarTransporte(ex, prazoTentativa));
        }
    }

    private IaErro classificarTransporte(RestClientException ex, long prazoTentativa) {
        if (nanoTime.getAsLong() - prazoTentativa >= 0) {
            return GEMINI_TIMEOUT;
        }
        for (Throwable causa = ex; causa != null; causa = causa.getCause()) {
            if (causa instanceof HttpTimeoutException || causa instanceof SocketTimeoutException) {
                return GEMINI_TIMEOUT;
            }
            if (causa instanceof SSLException || causa instanceof UnknownHostException) {
                return GEMINI_CONFIGURACAO;
            }
        }
        return ex instanceof ResourceAccessException ? GEMINI_CONEXAO : GEMINI_RESPOSTA_INVALIDA;
    }

    private static IaErro classificarStatus(int status) {
        return switch (status) {
            case 401, 402, 403, 404 -> GEMINI_CONFIGURACAO;
            case 408, 504 -> GEMINI_TIMEOUT;
            case 429 -> GEMINI_LIMITE_USO;
            case 500, 502 -> GEMINI_FALHA_TEMPORARIA;
            case 503 -> GEMINI_INDISPONIVEL;
            default -> GEMINI_REQUISICAO_REJEITADA;
        };
    }

    private void registrar(int tentativa, long inicio, String status) {
        LOG.info("gemini modelo={} tentativa={} status_http={} duracao_ms={}", properties.model(), tentativa,
                status, TimeUnit.NANOSECONDS.toMillis(nanoTime.getAsLong() - inicio));
    }

    record GeminiApiResponse(List<Candidate> candidates, PromptFeedback promptFeedback) {}

    record PromptFeedback(String blockReason) {}

    record Candidate(Content content, String finishReason) {}

    record Content(List<Part> parts) {}

    record Part(String text, Boolean thought) {}
}
