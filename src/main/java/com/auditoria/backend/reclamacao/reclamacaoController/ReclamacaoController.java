package com.auditoria.backend.reclamacao.reclamacaoController;

import com.auditoria.backend.reclamacao.reclamacaoService.DuplicidadeService;//Conecta com o detector de duplicata
import com.auditoria.backend.reclamacao.reclamacaoDTO.ReclamacaoDTO;
import com.auditoria.backend.reclamacao.reclamacaoDTO.ReclamacaoFormDTO;
import com.auditoria.backend.usuario.usuarioDTO.UsuarioDTO;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/reclamacao")
@CrossOrigin(origins = "http://localhost:5173")
public class ReclamacaoController {

//Guarda o service no controller
private final DuplicidadeService duplicidadeService;

public ReclamacaoController(DuplicidadeService duplicidadeService) {
    this.duplicidadeService = duplicidadeService;
}

    @PostMapping
    public ResponseEntity<ReclamacaoDTO> fazerReclamacao(@ModelAttribute ReclamacaoFormDTO dados) {

        duplicidadeService.validarERegistrar(dados.reclamacao());//Barra reclamacao repetida (devolve 409)

        // Testando se o arquivo chegou de verdade
        if (dados.arquivo() != null && !dados.arquivo().isEmpty()) {
            System.out.println("ARQUIVO RECEBIDO COM SUCESSO!");
            System.out.println("Nome do arquivo: " + dados.arquivo().getOriginalFilename());
            System.out.println("Tamanho (bytes): " + dados.arquivo().getSize());
        } else {
            System.out.println("Nenhum arquivo enviado.");
        }

        UsuarioDTO usuario = null;
        if (Boolean.FALSE.equals(dados.isAnonimo())) {
            usuario = new UsuarioDTO(
                    1, 2, 1,
                    "Mariana Oliveira Santos",
                    "mariana.santos@example.com",
                    "(79) 99912-3456",
                    "SenhaTeste123!",
                    "COLABORADOR",
                    true,
                    LocalDateTime.now()
            );
        }

        ReclamacaoDTO resposta = new ReclamacaoDTO(usuario, dados);
        // Testando se resposta está funcionando
        System.out.println(resposta);
        return ResponseEntity.status(HttpStatus.CREATED).body(resposta);
    }
}
