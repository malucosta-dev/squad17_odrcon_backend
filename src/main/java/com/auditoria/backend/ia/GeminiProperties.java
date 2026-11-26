package com.auditoria.backend.ia;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;
import java.time.Duration;

@ConfigurationProperties("gemini.api")
record GeminiProperties(
        String key,
        String model,
        URI baseUrl,
        Duration timeout,
        Duration totalTimeout,
        int maxAttempts,
        Duration initialBackoff,
        int maxOutputTokens,
        double temperature
) {
    GeminiProperties {
        if (model == null || !model.matches("[a-zA-Z0-9][a-zA-Z0-9._-]*")) {
            throw new IllegalArgumentException("Modelo Gemini inválido.");
        }
        if (baseUrl == null || !"https".equalsIgnoreCase(baseUrl.getScheme()) || baseUrl.getHost() == null
                || baseUrl.getRawUserInfo() != null || baseUrl.getRawQuery() != null || baseUrl.getRawFragment() != null
                || baseUrl.getRawPath().isBlank() || baseUrl.getRawPath().endsWith("/")) {
            throw new IllegalArgumentException("A URL do Gemini deve usar HTTPS e indicar o caminho de modelos.");
        }
        if (foraDoIntervalo(timeout) || foraDoIntervalo(totalTimeout) || foraDoIntervalo(initialBackoff)
                || maxAttempts < 1 || maxAttempts > 5) {
            throw new IllegalArgumentException("Tempos ou quantidade de tentativas do Gemini inválidos.");
        }
        if (maxOutputTokens < 1 || maxOutputTokens > 65_536
                || !Double.isFinite(temperature) || temperature < 0 || temperature > 2) {
            throw new IllegalArgumentException("Parâmetros de geração do Gemini inválidos.");
        }
    }

    private static boolean foraDoIntervalo(Duration valor) {
        return valor == null || valor.compareTo(Duration.ofMillis(1)) < 0
                || valor.compareTo(Duration.ofMinutes(2)) > 0;
    }

    @Override
    public String toString() {
        return "GeminiProperties[model=" + model + "]";
    }
}
