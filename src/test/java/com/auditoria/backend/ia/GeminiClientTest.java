package com.auditoria.backend.ia;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.mock.http.client.MockClientHttpResponse;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.IntFunction;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(OutputCaptureExtension.class)
class GeminiClientTest {
    private static final String CHAVE = "credencial-simulada";
    private static final String TEXTO = "relato-privado-simulado";
    private static final String MODELO = "gemini-3.1-flash-lite";
    private static final String BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models";
    private static final Map<String, Object> CORPO = Map.of("texto", TEXTO);
    private final AtomicLong agora = new AtomicLong();
    private final List<Duration> timeouts = new ArrayList<>();

    @Test
    void repete503UmaVezERegistraSomenteMetadados(CapturedOutput output) {
        var cliente = criarCliente(tentativa -> tentativa == 1 ? HttpStatus.SERVICE_UNAVAILABLE : HttpStatus.OK);

        assertNotNull(cliente.gerar(CORPO));
        assertEquals(2, timeouts.size());
        assertTrue(output.getAll().contains("gemini modelo=gemini-3.1-flash-lite tentativa=1 status_http=503 duracao_ms="));
        assertTrue(output.getAll().contains("gemini modelo=gemini-3.1-flash-lite tentativa=2 status_http=200 duracao_ms="));
        assertFalse(output.getAll().contains(CHAVE));
        assertFalse(output.getAll().contains(TEXTO));
    }

    @Test
    void devolveErroDepoisDoLimiteDeTentativas() {
        var cliente = criarCliente(tentativa -> HttpStatus.SERVICE_UNAVAILABLE);

        var erro = assertThrows(IaIntegracaoException.class, () -> cliente.gerar(CORPO));

        assertEquals(IaErro.GEMINI_INDISPONIVEL, erro.getCodigo());
        assertEquals(2, timeouts.size());
    }

    @ParameterizedTest
    @ValueSource(ints = {400, 401, 402, 403, 404, 429})
    void naoRepeteErrosPermanentesOuLimiteDeUso(int status) {
        var cliente = criarCliente(tentativa -> HttpStatus.valueOf(status));

        var erro = assertThrows(IaIntegracaoException.class, () -> cliente.gerar(CORPO));

        assertEquals(1, timeouts.size());
        assertFalse(erro.getCodigo().temporario);
    }

    @Test
    void reduzTimeoutDaSegundaTentativaAoOrcamentoRestante() {
        var cliente = criarCliente(tentativa -> {
            agora.set(Duration.ofSeconds(14).toNanos());
            return tentativa == 1 ? HttpStatus.SERVICE_UNAVAILABLE : HttpStatus.OK;
        });

        assertNotNull(cliente.gerar(CORPO));
        assertEquals(List.of(Duration.ofSeconds(15), Duration.ofSeconds(6)), timeouts);
    }

    @Test
    void naoEsperaSeOIntervaloNaoCabeNoOrcamento() {
        var cliente = criarCliente(tentativa -> {
            agora.set(Duration.ofMillis(19_999).toNanos());
            return HttpStatus.SERVICE_UNAVAILABLE;
        });

        var erro = assertThrows(IaIntegracaoException.class, () -> cliente.gerar(CORPO));

        assertEquals(IaErro.GEMINI_INDISPONIVEL, erro.getCodigo());
        assertEquals(1, timeouts.size());
    }

    private GeminiClient criarCliente(IntFunction<HttpStatus> statusPorTentativa) {
        var properties = new GeminiProperties(CHAVE, MODELO,
                URI.create(BASE_URL), Duration.ofSeconds(15), Duration.ofSeconds(20),
                2, Duration.ofMillis(1), 4096, 0.1);
        var chamadas = new AtomicInteger();
        var restClient = RestClient.builder().baseUrl(BASE_URL)
                .requestFactory((uri, metodo) -> new MockClientHttpRequest(metodo, uri) {
                    @Override
                    protected MockClientHttpResponse executeInternal() {
                        var resposta = new MockClientHttpResponse(
                                "{\"candidates\":[]}".getBytes(StandardCharsets.UTF_8),
                                statusPorTentativa.apply(chamadas.incrementAndGet()));
                        resposta.getHeaders().setContentType(MediaType.APPLICATION_JSON);
                        return resposta;
                    }
                }).build();
        return new GeminiClient(properties, prazo -> {
            timeouts.add(Duration.ofNanos(prazo - agora.get()));
            return restClient;
        }, agora::get);
    }
}
