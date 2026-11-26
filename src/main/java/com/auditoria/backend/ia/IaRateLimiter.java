package com.auditoria.backend.ia;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Limitador de taxa (rate limiter) em memória para o endpoint de reformulação
 * com IA.
 *
 * <p>
 * Aplica dois limites independentes em janela deslizante:
 * <ul>
 * <li>Limite por sessão HTTP (anônima ou autenticada), protegendo contra abuso
 * individual.</li>
 * <li>Limite agregado por endereço IP (mais amplo), permitindo que múltiplos
 * funcionários
 * compartilhem o mesmo IP corporativo (NAT/proxy) sem bloqueio mútuo em uso
 * normal,
 * mas contendo ataques automatizados que rotacionem sessões.</li>
 * </ul>
 *
 * <p>
 * Privacidade: endereços IP e identificadores de sessão jamais são registrados
 * em logs
 * ou persistidos no banco de dados. As chaves em memória são derivadas via hash
 * SHA-256 com
 * sal aleatório efêmero gerado na inicialização da JVM.
 *
 * <p>
 * Limitação arquitetural: o estado é local à instância da JVM. Em implantações
 * com
 * múltiplas instâncias horizontais do backend, recomenda-se substituir ou
 * complementar
 * por armazenamento compartilhado (ex.: Redis + Bucket4j) ou rate limiting no
 * API Gateway.
 */
@Component
public class IaRateLimiter {

    public static final int DEFAULT_SESSION_MAX_REQUESTS = 10;
    public static final int DEFAULT_IP_MAX_REQUESTS = 60;
    public static final long DEFAULT_WINDOW_SECONDS = 60L;
    private static final int MAX_TRACKED_KEYS = 10_000;

    private static final String MENSAGEM_LIMITE_EXCEDIDO = "Limite de solicitações de reformulação atingido. Aguarde alguns instantes antes de tentar novamente.";

    private final int maxRequestsPerSession;
    private final int maxRequestsPerIp;
    private final Duration windowDuration;
    private final Set<String> trustedProxies;
    private final Clock clock;
    private final byte[] ephemeralSalt;

    private final ConcurrentHashMap<String, Deque<Instant>> sessionBuckets = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Deque<Instant>> ipBuckets = new ConcurrentHashMap<>();

    @Autowired
    public IaRateLimiter(
            @Value("${gemini.ratelimit.session-max-requests:" + DEFAULT_SESSION_MAX_REQUESTS + "}") int maxRequestsPerSession,
            @Value("${gemini.ratelimit.ip-max-requests:" + DEFAULT_IP_MAX_REQUESTS + "}") int maxRequestsPerIp,
            @Value("${gemini.ratelimit.window-seconds:" + DEFAULT_WINDOW_SECONDS + "}") long windowSeconds,
            @Value("${gemini.ratelimit.trusted-proxies:}") String trustedProxiesConfig) {
        this(
                maxRequestsPerSession,
                maxRequestsPerIp,
                windowSeconds,
                trustedProxiesConfig,
                Clock.systemUTC());
    }

    IaRateLimiter(
            int maxRequestsPerSession,
            int maxRequestsPerIp,
            long windowSeconds,
            String trustedProxiesConfig,
            Clock clock) {
        this.maxRequestsPerSession = Math.max(1, maxRequestsPerSession);
        this.maxRequestsPerIp = Math.max(1, maxRequestsPerIp);
        this.windowDuration = Duration.ofSeconds(Math.max(1L, windowSeconds));
        this.trustedProxies = parseTrustedProxies(trustedProxiesConfig);
        this.clock = clock != null ? clock : Clock.systemUTC();
        this.ephemeralSalt = new byte[32];
        new SecureRandom().nextBytes(this.ephemeralSalt);
    }

    public void verificarLimite(HttpServletRequest request) {
        if (request == null) {
            return;
        }

        Instant agora = Instant.now(clock);

        String sessionId = extrairIdentificadorSessao(request);
        if (sessionId != null && !sessionId.isBlank()) {
            String chaveSessao = anonimizarChave("SESSAO", sessionId);
            registrarOuBloquear(sessionBuckets, chaveSessao, maxRequestsPerSession, agora);
        }

        String clientIp = extrairIpCliente(request);
        if (clientIp != null && !clientIp.isBlank()) {
            String chaveIp = anonimizarChave("IP", clientIp);
            registrarOuBloquear(ipBuckets, chaveIp, maxRequestsPerIp, agora);
        }
    }

    private void registrarOuBloquear(
            ConcurrentHashMap<String, Deque<Instant>> mapa,
            String chaveAnonimizada,
            int limite,
            Instant agora) {
        if (mapa.size() > MAX_TRACKED_KEYS) {
            limparJanelasExpiradas(mapa, agora);
        }

        Deque<Instant> historico = mapa.computeIfAbsent(
                chaveAnonimizada,
                k -> new ArrayDeque<>());

        Instant limiteExpiracao = agora.minus(windowDuration);

        synchronized (historico) {
            while (!historico.isEmpty() && !historico.peekFirst().isAfter(limiteExpiracao)) {
                historico.pollFirst();
            }

            if (historico.size() >= limite) {
                Instant maisAntiga = historico.peekFirst();
                long retryAfter = 1L;
                if (maisAntiga != null) {
                    Duration restante = Duration.between(agora, maisAntiga.plus(windowDuration));
                    retryAfter = Math.max(1L, (long) Math.ceil(restante.toMillis() / 1000.0));
                }
                throw new IaRateLimitException(MENSAGEM_LIMITE_EXCEDIDO, retryAfter);
            }

            historico.addLast(agora);
        }
    }

    private void limparJanelasExpiradas(
            ConcurrentHashMap<String, Deque<Instant>> mapa,
            Instant agora) {
        Instant limiteExpiracao = agora.minus(windowDuration);
        mapa.forEach((chave, historico) -> {
            synchronized (historico) {
                while (!historico.isEmpty() && !historico.peekFirst().isAfter(limiteExpiracao)) {
                    historico.pollFirst();
                }
                if (historico.isEmpty()) {
                    mapa.remove(chave, historico);
                }
            }
        });
    }

    private String extrairIdentificadorSessao(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            session = request.getSession(true);
        }
        return session != null ? session.getId() : null;
    }

    String extrairIpCliente(HttpServletRequest request) {
        String remoteAddr = request.getRemoteAddr();
        if (remoteAddr == null || remoteAddr.isBlank()) {
            return "unknown";
        }

        String remoteNormalizado = remoteAddr.trim();

        // Só confia em X-Forwarded-For quando a conexão direta veio de um proxy
        // confiável configurado.
        if (!trustedProxies.isEmpty() && trustedProxies.contains(remoteNormalizado)) {
            String xff = request.getHeader("X-Forwarded-For");
            if (xff != null && !xff.isBlank()) {
                String[] partes = xff.split(",");
                for (int i = partes.length - 1; i >= 0; i--) {
                    String candidato = partes[i].trim();
                    if (!candidato.isEmpty() && !trustedProxies.contains(candidato)) {
                        return candidato;
                    }
                }
            }
            String xRealIp = request.getHeader("X-Real-IP");
            if (xRealIp != null && !xRealIp.isBlank()) {
                return xRealIp.trim();
            }
        }

        return remoteNormalizado;
    }

    private String anonimizarChave(String prefixo, String valorBruto) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(ephemeralSalt);
            digest.update(prefixo.getBytes(StandardCharsets.UTF_8));
            byte[] hash = digest.digest(valorBruto.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Algoritmo SHA-256 indisponível.", e);
        }
    }

    private static Set<String> parseTrustedProxies(String config) {
        if (config == null || config.isBlank()) {
            return Collections.emptySet();
        }
        Set<String> proxies = new HashSet<>();
        for (String item : config.split(",")) {
            if (item != null) {
                String limpo = item.trim();
                if (!limpo.isEmpty()) {
                    proxies.add(limpo);
                }
            }
        }
        return Collections.unmodifiableSet(proxies);
    }
}
