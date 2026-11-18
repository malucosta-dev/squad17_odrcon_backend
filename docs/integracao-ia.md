# Validacao da integracao com Gemini

## Configuracao

Configure `GEMINI_API_KEY` no `.env` da raiz ou no ambiente do processo que inicia o backend.
Nao inclua o valor em commits, capturas de tela, logs ou mensagens.
A aplicação carrega o .env da raiz por meio de spring.config.import, quando iniciada nessa pasta. Variáveis de ambiente têm prioridade.

As propriedades `gemini.api.model`, `gemini.api.base-url` e
`gemini.api.timeout-seconds` estao em `application.properties`.
A chave fica no backend; o frontend nao deve chamar o Google diretamente.

## Testes isolados, sem Neon

Com Maven e JDK configurados, execute na raiz:

```powershell
mvn "-Dtest=GeminiServiceTest,IaControllerTest,IaSegurancaTest" test
```

Esses testes simulam o Google e nao inicializam o banco.
Nao use o resultado como prova de acesso real ao Gemini.

## Endpoint protegido com Gemini real, sem usuario no Neon

`GeminiEndpointRealTest` carrega o controller, o cliente Gemini e a mesma
configuracao de seguranca da aplicacao. A identidade existe apenas no teste,
com CSRF e filtros habilitados. Nao carrega JPA, nao consulta o Neon e nao
abre uma rota publica. A chamada real consome a quota da chave configurada.

No PowerShell, com `GEMINI_API_KEY` ja disponivel no ambiente:

```powershell
$env:RUN_GEMINI_LIVE_TEST = 'true'
try {
    mvn "-Dtest=GeminiEndpointRealTest" test
} finally {
    Remove-Item Env:RUN_GEMINI_LIVE_TEST -ErrorAction SilentlyContinue
}
```

Sem essa opcao, o teste real fica ignorado para evitar chamadas ao Google
durante testes comuns. Esse teste usa `System.getenv("GEMINI_API_KEY")`,
entao a chave precisa estar no ambiente do processo de teste; a configuracao
da aplicacao nao basta para esse teste. Configure o ambiente pelo processo
ou pela IDE antes da execucao; nao coloque chaves no codigo ou na linha de comando.

O teste verifica HTTP 200, texto nao vazio e preservacao do protocolo ficticio
123. Isso comprova o percurso pelo controller e Gemini, mas nao o login com
senha no banco, CORS no navegador ou fidelidade de todos os textos possiveis.

`IaSegurancaTest` verifica tambem que acesso anonimo e acesso autenticado sem
CSRF recebem 403 e nao chegam a chamar o servico de IA.

## Resultado da validacao em 11/11/2026

O backend iniciou conectado ao Neon com ddl-auto=validate. Essa configuração valida o esquema, mas não garante conexão somente leitura.

Os itens abaixo sao resultados registrados no relatorio anterior, sem nova
execucao dos testes nesta revisao documental:

- Endpoint de CSRF respondeu HTTP 200.
- Gemini real respondeu pelo endpoint em MockMvc com filtros de seguranca,
  identidade simulada e token CSRF: HTTP 200.
- Execucao selecionada: 23 testes passaram, sem falhas ou testes ignorados.
- Suite completa: 23 testes passaram, sem falhas; o teste real foi ignorado
  intencionalmente nessa segunda execucao, pois ja havia passado separadamente.
  O teste de carregamento da aplicacao passou conectado ao Neon; nao foi
  comprovado que a conexao era somente leitura.
- Login real permanece pendente de conta existente com senha conhecida.
- Interface e CORS no navegador permanecem pendentes do frontend, que nao
  esta neste repositorio. O teste com MockMvc nao substitui essa validacao.

## Contrato HTTP

`POST /api/ia/reformular`, com `Content-Type: application/json`:

```json
{
  "textoOriginal": "O pedido ficticio 123 atrasou dois dias. Solicito uma solucao.",
  "instrucoesRevisor": "Manter os fatos e usar linguagem formal."
}
```

Resposta de sucesso:

```json
{
  "textoReformulado": "Texto reformulado pelo Gemini."
}
```

O texto original e obrigatorio e aceita ate 5000 caracteres. As instrucoes
sao opcionais e aceitam ate 1000 caracteres.

A rota usa a autenticacao existente por sessao. O cliente precisa conservar
o cookie de sessao e enviar o token CSRF obtido em `GET /api/auth/csrf`,
usando o nome de header retornado nesse endpoint. Nao desabilite CSRF ou
autenticacao para testar a IA. No navegador, confira tambem o preflight CORS.

Erros de validacao retornam 400. Chave ausente ou indisponibilidade/quota do
Google retornam 503. Timeout retorna 504. Erros de comunicacao e respostas
sem texto final utilizavel retornam 502. O corpo tratado contem `erro`.
Falhas dos filtros de seguranca seguem o contrato da autenticacao existente.

## Validacao real antes da entrega

1. Configure a chave localmente, sem divulgar o valor.
2. Use o ambiente de backend e a sessao de teste ja existentes. Iniciar a
   aplicacao completa exige a configuracao de banco existente; nao altere o Neon.
3. Envie somente uma reclamacao ficticia pelo endpoint autenticado.
4. Confirme status 200 e `textoReformulado` nao vazio.
5. Compare datas, valores, protocolos e solicitacoes com o original.
6. Confirme no frontend a exibicao do resultado e de erros, sem substituir
   automaticamente o relato original.

O prompt orienta fidelidade, mas nao garante ausencia de inferencias.
Revisao humana continua necessaria. Respostas interrompidas ou bloqueadas
nao sao apresentadas como uma reformulacao concluida.
