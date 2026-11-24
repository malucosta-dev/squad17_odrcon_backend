package com.auditoria.backend.ia;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@CrossOrigin(origins = "http://localhost:5173")
@RestController
@RequestMapping("/api/ia")
@Import(IaRateLimiter.class)
public class IaController {

    private final GeminiService geminiService;
    private final IaRateLimiter iaRateLimiter;

    @Autowired
    public IaController(GeminiService geminiService, IaRateLimiter iaRateLimiter) {
        this.geminiService = geminiService;
        this.iaRateLimiter = iaRateLimiter != null ? iaRateLimiter : new IaRateLimiter();
    }

    public IaController(GeminiService geminiService) {
        this(geminiService, new IaRateLimiter());
    }

    @PostMapping("/reformular")
    public ResponseEntity<ReformulacaoResponse> reformular(
            @Valid @RequestBody ReformulacaoRequest request,
            HttpServletRequest httpRequest
    ) {
        iaRateLimiter.verificarLimite(httpRequest);

        String instrucoes = request.instrucoesRevisor();
        String textoReformulado = (instrucoes == null || instrucoes.isBlank())
                ? geminiService.reformularReclamacao(request.textoOriginal())
                : geminiService.reformularReclamacao(request.textoOriginal(), instrucoes);

        return ResponseEntity.ok(
                new ReformulacaoResponse(textoReformulado)
        );
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> tratarValidacao(
            MethodArgumentNotValidException exception
    ) {
        FieldError fieldError = exception.getBindingResult().getFieldError();
        String mensagem = (fieldError != null && fieldError.getDefaultMessage() != null)
                ? fieldError.getDefaultMessage()
                : "Dados de entrada inválidos.";

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(Map.of("erro", mensagem));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> tratarArgumentoInvalido(
            IllegalArgumentException exception
    ) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(Map.of("erro", exception.getMessage()));
    }

    @ExceptionHandler(IaRateLimitException.class)
    public ResponseEntity<Map<String, String>> tratarLimiteRequisicoes(
            IaRateLimitException exception
    ) {
        return ResponseEntity
                .status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(exception.getRetryAfterSeconds()))
                .body(Map.of("erro", exception.getMessage()));
    }

    @ExceptionHandler(IaIntegracaoException.class)
    public ResponseEntity<Map<String, String>> tratarErroIntegracao(
            IaIntegracaoException exception
    ) {
        return ResponseEntity
                .status(exception.getStatus())
                .body(Map.of("erro", exception.getMessage()));
    }
}
