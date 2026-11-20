package com.auditoria.backend.reclamacao.reclamacaoController;

import com.auditoria.backend.manifestacao.Manifestacao;
import com.auditoria.backend.manifestacao.ManifestacaoRepository;
import com.auditoria.backend.reclamacao.reclamacaoService.DuplicidadeService;//Conecta com o detector de duplicata
import com.auditoria.backend.reclamacao.reclamacaoDTO.ReclamacaoDTO;
import com.auditoria.backend.reclamacao.reclamacaoDTO.ReclamacaoFormDTO;
import com.auditoria.backend.reclamacao.reclamacaoDTO.ReclamacaoResponseDTO;
import com.auditoria.backend.usuario.usuarioDTO.UsuarioDTO;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/reclamacao")
@CrossOrigin(origins = "http://localhost:5173")
public class ReclamacaoController {

    private static final String TIPO_RECLAMACAO = "RECLAMACAO";

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
            duplicidadeService.validarERegistrar(form.reclamacao());//Barra reclamacao repetida (devolve 409)

            Integer idEmpresa = 0;
            Integer idUnidade = 0;

            // Usuario fake. Precisamos de um jeito de identificar o usuario logado.
            UsuarioDTO usuario = null;
            if (Boolean.FALSE.equals(form.isAnonimo())) {
                usuario = new UsuarioDTO(
                        1, null, 1,
                        "Teste",
                        "teste@example.com",
                        "(99) 9999-9999",
                        "SenhaTeste123!",
                        "COLABORADOR",
                        true,
                        LocalDateTime.now()
                );
                idEmpresa = usuario.idEmpresa();
                idUnidade = usuario.idUnidade();

            } else {
                idEmpresa = form.idEmpresa();
                idUnidade = form.idUnidade();
            }

            ReclamacaoDTO resposta = new ReclamacaoDTO(usuario, form);

            manifestacaoRepository.save(new Manifestacao(
                    null,                                          // 1. idManifestacao (gerado pelo banco)
                    idUnidade,                                                  // 2. idUnidade
                    idEmpresa,                                                  // 3. idEmpresa (obrigatório)
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

    // Lista as reclamações salvas no banco (mais recentes primeiro).
    // Filtros opcionais: ?idEmpresa=3 e ?status=ABERTA
    // Com usuário logado, a empresa vem sempre da sessão (o parâmetro idEmpresa é ignorado).
    @GetMapping
    public ResponseEntity<List<ReclamacaoResponseDTO>> listarReclamacoes(
            Authentication auth,
            @RequestParam(required = false) Integer idEmpresa,
            @RequestParam(required = false) String status) {

        Integer empresaLogada = idEmpresaDoLogin(auth);
        Integer empresa = empresaLogada != null ? empresaLogada : idEmpresa;
        String statusFiltro = (status == null || status.isBlank()) ? null : status.trim();

        List<Manifestacao> encontradas;
        if (empresa != null && statusFiltro != null) {
            encontradas = manifestacaoRepository
                    .findByTipoIgnoreCaseAndIdEmpresaAndStatusIgnoreCaseOrderByCriadoEmDesc(
                            TIPO_RECLAMACAO, empresa, statusFiltro);
        } else if (empresa != null) {
            encontradas = manifestacaoRepository
                    .findByTipoIgnoreCaseAndIdEmpresaOrderByCriadoEmDesc(TIPO_RECLAMACAO, empresa);
        } else if (statusFiltro != null) {
            encontradas = manifestacaoRepository
                    .findByTipoIgnoreCaseAndStatusIgnoreCaseOrderByCriadoEmDesc(TIPO_RECLAMACAO, statusFiltro);
        } else {
            encontradas = manifestacaoRepository.findByTipoIgnoreCaseOrderByCriadoEmDesc(TIPO_RECLAMACAO);
        }

        return ResponseEntity.ok(
                encontradas.stream().map(ReclamacaoResponseDTO::from).toList());
    }

    // Busca uma reclamação pelo id.
    @GetMapping("/{id}")
    public ResponseEntity<Object> buscarReclamacaoPorId(Authentication auth, @PathVariable Integer id) {
        Optional<Manifestacao> encontrada =
                manifestacaoRepository.findByIdManifestacaoAndTipoIgnoreCase(id, TIPO_RECLAMACAO);

        Integer empresaLogada = idEmpresaDoLogin(auth);
        boolean deOutraEmpresa = empresaLogada != null
                && encontrada.isPresent()
                && !empresaLogada.equals(encontrada.get().getIdEmpresa());

        // Devolve 404 (e não 403) para não revelar que o id existe em outra empresa.
        if (encontrada.isEmpty() || deOutraEmpresa) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Reclamação não encontrada.");
        }

        return ResponseEntity.ok(ReclamacaoResponseDTO.from(encontrada.get()));
    }

    @GetMapping("/protocolo/{codigo}")
    public ResponseEntity<Object> procurarReclamacao(@PathVariable String codigo){
        Optional<Manifestacao> codigoOptional = manifestacaoRepository.findByProtocoloUnico(codigo);
        if (codigoOptional.isEmpty()){
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Protocolo não encontrado.");
        }

        return ResponseEntity.ok(ReclamacaoResponseDTO.from(codigoOptional.get()));
    }

    // Principal da sessão no formato "idEmpresa:idUsuario" (ver AuthController).
    // Retorna null quando não há login (só acontece no perfil dev).
    private Integer idEmpresaDoLogin(Authentication auth) {
        boolean semLogin = auth == null || auth instanceof AnonymousAuthenticationToken;
        if (semLogin) {
            return null;
        }

        try {
            return Integer.valueOf(auth.getName().split(":")[0]);
        } catch (NumberFormatException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
    }
}
