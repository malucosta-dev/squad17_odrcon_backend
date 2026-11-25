package com.auditoria.backend.ia;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@CrossOrigin(origins = "http://localhost:5173")
@RestController
@RequestMapping("/api/ia")
public class IaController {

    private final GeminiService geminiService;
    private final IaRateLimiter iaRateLimiter;

    public IaController(GeminiService geminiService, IaRateLimiter iaRateLimiter) {
        this.geminiService = geminiService;
        this.iaRateLimiter = iaRateLimiter;
    }

    @PostMapping("/reformular")
    public ResponseEntity<ReformulacaoResponse> reformular(
            @Valid @RequestBody ReformulacaoRequest request,
            HttpServletRequest httpRequest
    ) {
        iaRateLimiter.verificarLimite(httpRequest);

        String textoReformulado = geminiService.reformularReclamacao(
                request.textoOriginal()
        );

        return ResponseEntity.ok(
                new ReformulacaoResponse(textoReformulado)
        );
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Falha> tratarValidacao(
            MethodArgumentNotValidException exception
    ) {
        FieldError fieldError = exception.getBindingResult().getFieldError();
        String mensagem = (fieldError != null && fieldError.getDefaultMessage() != null)
                ? fieldError.getDefaultMessage()
                : IaErro.IA_ENTRADA_INVALIDA.mensagem;

        return ResponseEntity
                .badRequest()
                .body(new Falha(IaErro.IA_ENTRADA_INVALIDA, mensagem));
    }

    @ExceptionHandler({IllegalArgumentException.class, HttpMessageNotReadableException.class})
    public ResponseEntity<Falha> tratarEntradaInvalida() {
        var codigo = IaErro.IA_ENTRADA_INVALIDA;
        return ResponseEntity.badRequest().body(new Falha(codigo, codigo.mensagem));
    }

    @ExceptionHandler(IaRateLimitException.class)
    public ResponseEntity<Falha> tratarLimiteRequisicoes(
            IaRateLimitException exception
    ) {
        return ResponseEntity
                .status(IaErro.IA_LIMITE_REQUISICOES.status)
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(exception.getRetryAfterSeconds()))
                .body(new Falha(IaErro.IA_LIMITE_REQUISICOES, exception.getMessage()));
    }

    @ExceptionHandler(IaIntegracaoException.class)
    public ResponseEntity<Falha> tratarErroIntegracao(
            IaIntegracaoException exception
    ) {
        return ResponseEntity
                .status(exception.getStatus())
                .body(new Falha(exception.getCodigo(), exception.getMessage()));
    }

    record Falha(IaErro codigo, String erro) {}
}
