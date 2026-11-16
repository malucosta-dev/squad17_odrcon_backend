package com.auditoria.backend.auth;

import java.util.Map;

import jakarta.validation.Valid;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/auth")
public class CadastroController {

    private final CadastroService cadastroService;

    public CadastroController(CadastroService cadastroService) {
        this.cadastroService = cadastroService;
    }

    public record RegistroResponse(
            Integer idUsuario,
            String nome,
            Integer idEmpresa,
            String perfil
    ) {}

    @PostMapping("/registro")
    public ResponseEntity<RegistroResponse> cadastrar(
            @Valid @RequestBody RegistroRequest request
    ) {
        Usuario usuario = cadastroService.cadastrar(request);

        RegistroResponse resposta = new RegistroResponse(
                usuario.getIdUsuario(),
                usuario.getNomeUsuario(),
                usuario.getIdEmpresa(),
                usuario.getPerfilUsuario()
        );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(resposta);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> tratarValidacao(
            MethodArgumentNotValidException exception
    ) {
        String mensagem = exception.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(erro -> erro.getDefaultMessage())
                .findFirst()
                .orElse("Confira os dados preenchidos.");

        return ResponseEntity.badRequest()
                .body(Map.of("erro", mensagem));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> tratarRegra(
            ResponseStatusException exception
    ) {
        String mensagem = exception.getReason() != null
                ? exception.getReason()
                : "Não foi possível concluir o cadastro.";

        return ResponseEntity
                .status(exception.getStatusCode())
                .body(Map.of("erro", mensagem));
    }

    // Também cobre conflitos entre cadastros simultâneos.
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, String>> tratarConflito(
            DataIntegrityViolationException exception
    ) {
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(Map.of(
                        "erro",
                        "Não foi possível cadastrar com esses dados. "
                        + "O e-mail pode já estar cadastrado nesta empresa."
                ));
    }
}