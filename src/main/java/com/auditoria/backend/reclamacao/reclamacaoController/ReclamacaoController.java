package com.auditoria.backend.reclamacao.reclamacaoController;

import com.auditoria.backend.anexo.AnexoService;
import com.auditoria.backend.auth.Usuario;
import com.auditoria.backend.auth.UsuarioRepository;
import com.auditoria.backend.manifestacao.Manifestacao;
import com.auditoria.backend.manifestacao.ManifestacaoRepository;
import com.auditoria.backend.reclamacao.reclamacaoDTO.ReclamacaoFormDTO;
import com.auditoria.backend.reclamacao.reclamacaoService.DuplicidadeService;
import com.auditoria.backend.solucao.Proposta_solucao;
import com.auditoria.backend.solucao.Proposta_solucaoRepository;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/reclamacao")
@CrossOrigin(origins = "http://localhost:5173")
public class ReclamacaoController {

    @Autowired
    private ManifestacaoRepository manifestacaoRepository;

    @Autowired
    private Proposta_solucaoRepository solucaoRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private AnexoService anexoService;

    private final DuplicidadeService duplicidadeService;

    public ReclamacaoController(DuplicidadeService duplicidadeService) {
        this.duplicidadeService = duplicidadeService;
    }

    @PostMapping
    @Transactional
    public ResponseEntity<String> fazerReclamacao(
            @ModelAttribute ReclamacaoFormDTO form,
            Authentication authentication
    ) {
        if (form.isAnonimo() == null) {
            return ResponseEntity.badRequest().body("Informe se o envio é anônimo.");
        }

        boolean anonimo = Boolean.TRUE.equals(form.isAnonimo());
        Integer idEmpresa = form.idEmpresa();
        Integer idUnidade = form.idUnidade();
        Integer idSolicitante = null;

        if (!anonimo) {
            if (authentication == null || !authentication.isAuthenticated()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body("Faça login para enviar uma reclamação identificada.");
            }

            // A sessão da ODRCON identifica o usuário como empresa:usuario.
            String[] identificacao = authentication.getName().split(":", -1);
            if (identificacao.length != 2) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body("Sessão inválida. Faça login novamente.");
            }

            Integer idEmpresaSessao;
            Integer idUsuarioSessao;
            try {
                idEmpresaSessao = Integer.valueOf(identificacao[0]);
                idUsuarioSessao = Integer.valueOf(identificacao[1]);
            } catch (NumberFormatException exception) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body("Sessão inválida. Faça login novamente.");
            }

            Optional<Usuario> usuarioOpt = usuarioRepository.findById(idUsuarioSessao)
                    .filter(usuario -> idEmpresaSessao.equals(usuario.getIdEmpresa()))
                    .filter(usuario -> Boolean.TRUE.equals(usuario.getAtivo()));

            if (usuarioOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body("Sessão inválida. Faça login novamente.");
            }

            Usuario usuario = usuarioOpt.get();
            idEmpresa = usuario.getIdEmpresa();
            idUnidade = usuario.getIdUnidade();
            idSolicitante = usuario.getIdUsuario();
        }

        if (idEmpresa == null || idEmpresa <= 0) {
            return ResponseEntity.badRequest().body("Selecione uma empresa válida.");
        }

        boolean temSolucao = Boolean.TRUE.equals(form.hasSolucao());
        if (temSolucao && (form.solucao() == null || form.solucao().isBlank())) {
            return ResponseEntity.badRequest().body("Informe a solução proposta.");
        }

        anexoService.validar(form.arquivo());
        duplicidadeService.validarERegistrar(form.reclamacao());

        Manifestacao manifestacaoSalva = manifestacaoRepository.save(new Manifestacao(
                null,                         // idManifestacao
                idUnidade,                    // idUnidade
                idEmpresa,                    // idEmpresa
                idSolicitante,                // idSolicitante
                null,                         // idResponsavel
                "RECLAMACAO",                 // tipo
                "RECEBIDO",                   // status
                null,                         // prioridade
                form.reclamacao(),            // descricaoOriginal
                null,                         // descricaoTratada
                null,                         // dadosEstruturados
                null,                         // prazoResposta
                0,                            // nivelEscalonamento
                null,                         // avaliacaoResultado
                null,                         // avaliacaoNota
                null,                         // criadoEm
                null,                         // atualizadoEm
                "WEB",                        // canal
                UUID.randomUUID().toString(), // protocoloUnico
                anonimo,                      // anonimo
                "PENDENTE",                   // statusProcessamento
                null                          // nivelGravidade
        ));

        anexoService.salvar(manifestacaoSalva, form.arquivo());

        if (temSolucao) {
            boolean desejaAcordo = Boolean.TRUE.equals(form.hasAcordo());

            Proposta_solucao proposta = new Proposta_solucao(
                    null,
                    manifestacaoSalva,
                    form.solucao(),
                    desejaAcordo,
                    desejaAcordo ? "AGUARDANDO_ACORDO" : "PENDENTE",
                    null,
                    null
            );

            proposta.registrarAutoria(idSolicitante, "SOLICITANTE");
            solucaoRepository.save(proposta);
        }

        return ResponseEntity.status(HttpStatus.CREATED)
                .body("Reclamacao criada com sucesso.");
    }

    @GetMapping("/protocolo/{codigo}")
    public ResponseEntity<Object> procurarReclamacao(@PathVariable String codigo) {
        Optional<Manifestacao> codigoOptional =
                manifestacaoRepository.findByProtocoloUnico(codigo);

        if (codigoOptional.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("Protocolo não encontrado.");
        }

        Manifestacao manifestacaoEncontrada = codigoOptional.get();
        return ResponseEntity.ok("Manifestacao encontrada: " + manifestacaoEncontrada);
    }
}