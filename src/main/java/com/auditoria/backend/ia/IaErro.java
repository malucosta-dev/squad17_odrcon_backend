package com.auditoria.backend.ia;

import org.springframework.http.HttpStatus;

enum IaErro {
    IA_ENTRADA_INVALIDA(HttpStatus.BAD_REQUEST, "Dados de entrada inválidos."),
    IA_LIMITE_REQUISICOES(HttpStatus.TOO_MANY_REQUESTS, "Limite de solicitações atingido."),
    IA_FORA_DO_ESCOPO(HttpStatus.UNPROCESSABLE_CONTENT,
            "O texto informado não contém um relato de reclamação ou denúncia válido para reformulação."),
    IA_CONTEUDO_BLOQUEADO(HttpStatus.UNPROCESSABLE_CONTENT,
            "O conteúdo acionou as restrições de segurança da IA. Revise o texto ou prossiga com o relato original."),
    IA_SUGESTAO_BLOQUEADA(HttpStatus.UNPROCESSABLE_CONTENT,
            "Não foi possível validar a reformulação gerada para o texto enviado."),
    GEMINI_LIMITE_USO(HttpStatus.SERVICE_UNAVAILABLE, "O Gemini atingiu um limite de uso. Tente novamente mais tarde."),
    GEMINI_CONFIGURACAO(HttpStatus.SERVICE_UNAVAILABLE, "A integração com o Gemini está indisponível."),
    GEMINI_REQUISICAO_REJEITADA(HttpStatus.BAD_GATEWAY, "O Gemini recusou o processamento da solicitação."),
    GEMINI_RESPOSTA_INVALIDA(HttpStatus.BAD_GATEWAY, "Não foi possível validar a reformulação gerada para o texto enviado."),
    GEMINI_INDISPONIVEL(HttpStatus.SERVICE_UNAVAILABLE, "O Gemini está temporariamente indisponível. Tente novamente em instantes.", true),
    GEMINI_FALHA_TEMPORARIA(HttpStatus.BAD_GATEWAY, "O Gemini retornou um erro temporário. Tente novamente em instantes.", true),
    GEMINI_CONEXAO(HttpStatus.BAD_GATEWAY, "Falha de conexão ao comunicar com o Gemini.", true),
    GEMINI_TIMEOUT(HttpStatus.GATEWAY_TIMEOUT, "Tempo limite excedido ao comunicar com a API do Gemini.", true);

    final HttpStatus status;
    final String mensagem;
    final boolean temporario;

    IaErro(HttpStatus status, String mensagem) {
        this(status, mensagem, false);
    }

    IaErro(HttpStatus status, String mensagem, boolean temporario) {
        this.status = status;
        this.mensagem = mensagem;
        this.temporario = temporario;
    }
}
