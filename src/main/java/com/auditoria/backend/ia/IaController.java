package com.auditoria.backend.ia;

import jakarta.validation.Valid;

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
public class IaController {

    private final GeminiService geminiService;

    public IaController(GeminiService geminiService) {
        this.geminiService = geminiService;
    }

    @PostMapping("/reformular")
    public ResponseEntity<ReformulacaoResponse> reformular(
            @Valid @RequestBody ReformulacaoRequest request
    ) {
        String textoReformulado = geminiService.reformularReclamacao(
                request.textoOriginal(),
                request.instrucoesRevisor()
        );

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

    @ExceptionHandler(IaIntegracaoException.class)
    public ResponseEntity<Map<String, String>> tratarErroIntegracao(
            IaIntegracaoException exception
    ) {
        return ResponseEntity
                .status(exception.getStatus())
                .body(Map.of("erro", exception.getMessage()));
    }
}

