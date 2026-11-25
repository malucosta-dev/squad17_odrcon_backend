# Validacao da integracao com Gemini

## Configuracao

Configure `GEMINI_API_KEY` e `GEMINI_API_MODEL=gemini-3.5-flash-lite` no `.env` da raiz ou no ambiente do processo que inicia o backend.
Nao inclua o valor da chave em commits, capturas de tela, logs ou mensagens.
A aplicacao carrega o `.env` da raiz por meio de `spring.config.import`, quando iniciada nessa pasta. Variaveis de ambiente tem prioridade.

As propriedades `gemini.api.model`, `gemini.api.base-url` e `gemini.api.timeout-seconds` estao em `application.properties`.
O `GeminiService` adota `gemini-3.5-flash-lite` como modelo padrao interno (`@Value("${GEMINI_API_MODEL:${gemini.api.model:gemini-3.5-flash-lite}}")`). Como `application.properties` define `gemini.api.model=gemini-3.1-flash-lite`, definir `GEMINI_API_MODEL=gemini-3.5-flash-lite` no ambiente ou no `.env` garante a selecao de `gemini-3.5-flash-lite` em tempo de execucao sem modificar `application.properties`.
Os limites de taxa do endpoint de IA (`gemini.ratelimit.*`) possuem valores padrao configurados diretamente em `IaRateLimiter` e podem ser sobrescritos por variaveis de ambiente ou propriedades sem alterar modulos externos.
A chave fica no backend; o frontend nao deve chamar o Google diretamente.

## Testes isolados, sem Neon

Com Maven e JDK configurados, execute na raiz:

```powershell
mvn "-Dtest=GeminiServiceTest,IaControllerTest,IaSegurancaTest" test
```

Esses testes simulam o Google e nao inicializam o banco.
Nao use o resultado como prova de acesso real ao Gemini.

## Endpoint com Gemini real, sem usuario no Neon

`GeminiEndpointRealTest` carrega o controller, o cliente Gemini e a mesma
configuracao de seguranca da aplicacao, com CSRF e filtros habilitados. Nao carrega JPA e nao consulta o Neon. A chamada real consome a quota da chave configurada.

No PowerShell, com `GEMINI_API_KEY` ja disponivel no ambiente:

```powershell
$env:RUN_GEMINI_LIVE_TEST = 'true'
try {
    mvn "-Dtest=GeminiEndpointRealTest" test
} finally {
    Remove-Item Env:RUN_GEMINI_LIVE_TEST -ErrorAction SilentlyContinue
}
```

## Contrato HTTP

`POST /api/ia/reformular`, com `Content-Type: application/json`:

Entrada:

```json
{"textoOriginal":"Texto da reclamação"}
```

Saida:

```json
{"textoReformulado":"Texto reformulado pela IA"}
```

- `textoOriginal`: obrigatorio, ate 5000 caracteres. A IA reformula automaticamente qualquer reclamacao valida, preservando estritamente os fatos relatados sem exigir instrucoes ou contexto adicional.

## Fluxo Anonimo, CSRF e Rate Limiting Restrito ao Modulo de IA

A rota `POST /api/ia/reformular` preserva o acesso anonimo e autenticado conforme definido em `SecurityConfig`, exigindo sempre o cookie de sessao e o token CSRF obtido em `GET /api/auth/csrf`.

Para proteger a quota da API dentro do proprio modulo de IA (`IaRateLimiter`), sem exigir identificacao do denunciante e sem bloquear funcionarios que compartilham o mesmo IP corporativo (NAT/proxy):

- `gemini.ratelimit.session-max-requests` (padrao `10`): limite de 10 requisicoes por sessao a cada janela de 60 segundos.
- `gemini.ratelimit.ip-max-requests` (padrao `60`): limite agregado de 60 requisicoes por IP a cada janela de 60 segundos.
- `gemini.ratelimit.window-seconds` (padrao `60`): duracao da janela em segundos.
- `gemini.ratelimit.trusted-proxies` (padrao vazio): lista opcional de IPs de proxies reversos confiaveis (separados por virgula). Cabecalhos `X-Forwarded-For` e `X-Real-IP` sao ignorados caso a requisicao nao provenha de um proxy confiavel configurado.

Nenhum IP ou identificador de sessao e gravado em logs ou persistido junto as denuncias. Em memoria, os identificadores sao anonimizados com hash SHA-256 e sal efemero por inicializacao.

> **Limitacoes conhecidas do Rate Limiting local:**
> 1. **Sessao e IP:** Um cliente mal-intencionado que descarte cookies e solicite uma nova sessao + token CSRF a cada chamada contorna o limite por sessao (`10 req/min`), mas permanece contido pelo limite agregado por IP (`60 req/min`).
> 2. **Multiplas instancias:** O `IaRateLimiter` mantem contadores em memoria local da JVM. Em ambientes com multiplas instancias horizontais do backend, recomenda-se utilizar rate limiting compartilhado (ex.: Redis + Bucket4j) ou no API Gateway/Ingress.

## Codigos de Resposta e Erros

- `200 OK`: reformulacao concluida com sucesso.
- `400 Bad Request`: falha de validacao do campo de entrada (`textoOriginal` em branco ou > 5000 caracteres).
- `403 Forbidden`: token CSRF ausente ou invalido.
- `422 Unprocessable Content`: bloqueio pelos filtros de seguranca da IA (`promptFeedback.blockReason` ou `candidate.finishReason`), entrada sem relato de reclamacao valido ou bloqueio defensivo de saida.
- `429 Too Many Requests`: limite de requisicoes por sessao ou IP atingido (inclui cabecalho `Retry-After`).
- `502 Bad Gateway`: falha tecnica de comunicacao ou resposta truncada/invalida da API externa.
- `503 Service Unavailable`: chave `GEMINI_API_KEY` ausente ou indisponibilidade/quota da API externa.
- `504 Gateway Timeout`: tempo limite excedido na comunicacao com a API externa.
