package com.auditoria.backend.reclamacao.reclamacaoController;

import com.auditoria.backend.manifestacao.Manifestacao;
import com.auditoria.backend.manifestacao.ManifestacaoRepository;
import com.auditoria.backend.reclamacao.reclamacaoService.DuplicidadeService;//Conecta com o detector de duplicata
import com.auditoria.backend.reclamacao.reclamacaoDTO.ReclamacaoDTO;
import com.auditoria.backend.reclamacao.reclamacaoDTO.ReclamacaoFormDTO;
import com.auditoria.backend.usuario.usuarioDTO.UsuarioDTO;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.Optional;

@RestController
@RequestMapping("/api/reclamacao")
@CrossOrigin(origins = "http://localhost:5173")
public class ReclamacaoController {

    // Manifestacao repository
    @Autowired
    private ManifestacaoRepository manifestacaoRepository;

    //Guarda o service no controller
    private final DuplicidadeService duplicidadeService;

    public ReclamacaoController(DuplicidadeService duplicidadeService) {
        this.duplicidadeService = duplicidadeService;
    }

        @PostMapping
        @Transactional
        public ResponseEntity<String> fazerReclamacao(@ModelAttribute ReclamacaoFormDTO form) {
            System.out.println("CHEGUEI");

            duplicidadeService.validarERegistrar(form.reclamacao());//Barra reclamacao repetida (devolve 409)

            // Testando se o arquivo chegou de verdade
            if (form.arquivo() != null && !form.arquivo().isEmpty()) {
                System.out.println("ARQUIVO RECEBIDO COM SUCESSO!");
                System.out.println("Nome do arquivo: " + form.arquivo().getOriginalFilename());
                System.out.println("Tamanho (bytes): " + form.arquivo().getSize());
            } else {
                System.out.println("Nenhum arquivo enviado.");
            }

            UsuarioDTO usuario = null;
            if (Boolean.FALSE.equals(form.isAnonimo())) {
                usuario = new UsuarioDTO(
                        999, 1, 1,
                        "Teste",
                        "teste@example.com",
                        "(99) 9999-9999",
                        "SenhaTeste123!",
                        "COLABORADOR",
                        true,
                        LocalDateTime.now()
                );
            }

            ReclamacaoDTO resposta = new ReclamacaoDTO(usuario, form);

            manifestacaoRepository.save(new Manifestacao(
                    null,                                          // 1. idManifestacao (gerado pelo banco)
                    resposta.usuario().idUnidade(),                             // 2. idUnidade
                    resposta.usuario().idUnidade(),                             // 3. idEmpresa (obrigatório)
                    usuario != null ? resposta.usuario().idUsuario() : null,    // 4. idSolicitante
                    null,                                                       // 5. idResponsavel
                    "RECLAMACAO",                                               // 6. tipo (obrigatório)
                    "ABERTA",                                                   // 7. status (obrigatório)
                    null,                                                       // 8. prioridade
                    resposta.form().reclamacao(),                               // 9. descricaoOriginal (obrigatório)
                    null,                                                       // 10. descricaoTratada
                    null,                                                       // 11. dadosEstruturados
                    null,                                                       // 12. prazoResposta
                    0,                                                          // 13. nivelEscalonamento
                    null,                                                       // 14. avaliacaoResultado
                    null,                                                       // 15. avaliacaoNota
                    LocalDateTime.now(),                                        // 16. criadoEm (obrigatório)
                    LocalDateTime.now(),                                        // 17. atualizadoEm (obrigatório)
                    "WEB",                                                      // 18. canal
                    java.util.UUID.randomUUID().toString(),                     // 19. protocoloUnico (obrigatório)
                    resposta.form().isAnonimo(),                                // 20. anonimo (obrigatório)
                    "PENDENTE",                                                 // 21. statusProcessamento (obrigatório)
                    null                                                        // 22. nivelGravidade
            ));

            // Testando se resposta está funcionando
            System.out.println(resposta);

            return ResponseEntity.status(HttpStatus.CREATED).body("Reclamacao criada com sucesso.");
    }

    @GetMapping("/protocolo/{codigo}")
    public ResponseEntity<Object> procurarReclamacao(@PathVariable String codigo){
        Optional<Manifestacao> codigoOptional = manifestacaoRepository.findByProtocoloUnico(codigo);
        if (codigoOptional.isEmpty()){
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Protocolo não encontrado.");
        }

        Manifestacao manifestacaoEncontrada = codigoOptional.get();

        return ResponseEntity.ok("Manifestacao encontrada: " + manifestacaoEncontrada);
    }
}
