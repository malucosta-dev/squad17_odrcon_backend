package com.auditoria.backend.solucao;

import com.auditoria.backend.auth.Usuario;
import com.auditoria.backend.auth.UsuarioRepository;
import com.auditoria.backend.manifestacao.Manifestacao;
import com.auditoria.backend.manifestacao.ManifestacaoRepository;
import com.auditoria.backend.solucao.solucaoDTO.CriarSolucaoDTO;
import jakarta.transaction.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

@RestController
@RequestMapping("/api/solucoes")
@CrossOrigin(origins = "http://localhost:5173")
public class SolucaoController {

    private static final Set<String> PERFIS_EQUIPE = Set.of(
            "ROLE_ADMIN",
            "ROLE_RESPONSAVEL",
            "ROLE_COLABORADOR"
    );

    private final Proposta_solucaoRepository solucaoRepository;
    private final ManifestacaoRepository manifestacaoRepository;
    private final UsuarioRepository usuarioRepository;

    public SolucaoController(
            Proposta_solucaoRepository solucaoRepository,
            ManifestacaoRepository manifestacaoRepository,
            UsuarioRepository usuarioRepository
    ) {
        this.solucaoRepository = solucaoRepository;
        this.manifestacaoRepository = manifestacaoRepository;
        this.usuarioRepository = usuarioRepository;
    }

    public record PropostaResumo(
            Integer idProposta,
            String descricaoProposta,
            Boolean desejaAcordo,
            String statusProposta,
            String autorExibicao
    ) {}

    @PostMapping("/reclamacao/{idManifestacao}")
    @Transactional
    public ResponseEntity<String> criarProposta(
            @PathVariable Integer idManifestacao,
            @RequestBody CriarSolucaoDTO dto,
            Authentication authentication
    ) {
        if (dto.descricaoProposta() == null || dto.descricaoProposta().isBlank()) {
            return ResponseEntity.badRequest()
                    .body("A descrição da proposta é obrigatória.");
        }

        Optional<Usuario> autorOpt = usuarioDaSessao(authentication);
        if (autorOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body("Faça login para enviar uma proposta.");
        }

        Usuario autor = autorOpt.get();
        Optional<Manifestacao> manifestacaoOpt =
                buscarManifestacaoDaEmpresa(idManifestacao, autor);

        if (manifestacaoOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("Reclamação não encontrada.");
        }

        Manifestacao manifestacao = manifestacaoOpt.get();
        boolean solicitante = ehSolicitante(autor, manifestacao);

        if (!solicitante && !ehEquipe(authentication)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body("Você não pode propor uma solução para esta reclamação.");
        }

        if ("RESOLVIDA".equalsIgnoreCase(manifestacao.getStatus())) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body("Não é possível propor soluções para uma reclamação já resolvida.");
        }

        Proposta_solucao proposta = new Proposta_solucao(
                null,
                manifestacao,
                dto.descricaoProposta(),
                Boolean.TRUE.equals(dto.desejaAcordo()),
                "PENDENTE",
                null,
                null
        );

        proposta.registrarAutoria(
                autor.getIdUsuario(),
                solicitante ? "SOLICITANTE" : "EQUIPE"
        );
        solucaoRepository.save(proposta);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body("Proposta de solução criada com sucesso.");
    }

    @GetMapping("/reclamacao/{idManifestacao}")
    @Transactional
    public ResponseEntity<List<PropostaResumo>> listarPropostas(
            @PathVariable Integer idManifestacao,
            Authentication authentication
    ) {
        Optional<Usuario> usuarioOpt = usuarioDaSessao(authentication);
        if (usuarioOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        Usuario usuario = usuarioOpt.get();
        Optional<Manifestacao> manifestacaoOpt =
                buscarManifestacaoDaEmpresa(idManifestacao, usuario);

        if (manifestacaoOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Manifestacao manifestacao = manifestacaoOpt.get();
        if (!ehSolicitante(usuario, manifestacao) && !ehEquipe(authentication)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        List<PropostaResumo> propostas = solucaoRepository
                .findByManifestacao_IdManifestacao(idManifestacao)
                .stream()
                .map(proposta -> new PropostaResumo(
                        proposta.getIdProposta(),
                        proposta.getDescricaoProposta(),
                        proposta.getDesejaAcordo(),
                        proposta.getStatusProposta(),
                        autorExibicao(proposta, manifestacao)
                ))
                .toList();

        return ResponseEntity.ok(propostas);
    }

    @PatchMapping("/{idProposta}/recusar")
    @Transactional
    public ResponseEntity<String> recusarProposta(@PathVariable Integer idProposta) {
        Optional<Proposta_solucao> propostaOpt = solucaoRepository.findById(idProposta);
        if (propostaOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("Proposta de solução não encontrada.");
        }

        Proposta_solucao proposta = propostaOpt.get();
        if (propostaFinalizada(proposta)) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body("Esta proposta já foi finalizada com status: "
                            + proposta.getStatusProposta());
        }

        proposta.setStatusProposta("RECUSADA");
        solucaoRepository.save(proposta);

        return ResponseEntity.ok("Proposta recusada com sucesso.");
    }

    @PatchMapping("/{idProposta}/aceitar")
    @Transactional
    public ResponseEntity<String> aceitarProposta(@PathVariable Integer idProposta) {
        Optional<Proposta_solucao> propostaOpt = solucaoRepository.findById(idProposta);
        if (propostaOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("Proposta de solução não encontrada.");
        }

        Proposta_solucao proposta = propostaOpt.get();
        if (propostaFinalizada(proposta)) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body("Esta proposta já foi finalizada com status: "
                            + proposta.getStatusProposta());
        }

        proposta.setStatusProposta("ACEITA");
        solucaoRepository.save(proposta);

        Manifestacao manifestacao = proposta.getManifestacao();
        for (Proposta_solucao outra : solucaoRepository
                .findByManifestacao_IdManifestacao(manifestacao.getIdManifestacao())) {
            boolean outraProposta = !outra.getIdProposta().equals(proposta.getIdProposta());
            boolean emAberto = "PENDENTE".equalsIgnoreCase(outra.getStatusProposta())
                    || "AGUARDANDO_ACORDO".equalsIgnoreCase(outra.getStatusProposta());

            if (outraProposta && emAberto) {
                outra.setStatusProposta("RECUSADA");
                solucaoRepository.save(outra);
            }
        }

        boolean desejaAcordo = Boolean.TRUE.equals(proposta.getDesejaAcordo());
        manifestacao.setStatus(desejaAcordo ? "EM_ACORDO" : "RESOLVIDA");
        manifestacaoRepository.save(manifestacao);

        return ResponseEntity.ok("Proposta aceita com sucesso.");
    }

    private Optional<Usuario> usuarioDaSessao(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return Optional.empty();
        }

        // A sessão da ODRCON identifica o usuário como empresa:usuario.
        String[] identificacao = authentication.getName().split(":", -1);
        if (identificacao.length != 2) {
            return Optional.empty();
        }

        Integer idEmpresa;
        Integer idUsuario;
        try {
            idEmpresa = Integer.valueOf(identificacao[0]);
            idUsuario = Integer.valueOf(identificacao[1]);
        } catch (NumberFormatException exception) {
            return Optional.empty();
        }

        return usuarioRepository.findById(idUsuario)
                .filter(usuario -> idEmpresa.equals(usuario.getIdEmpresa()))
                .filter(usuario -> Boolean.TRUE.equals(usuario.getAtivo()));
    }

    private Optional<Manifestacao> buscarManifestacaoDaEmpresa(
            Integer idManifestacao,
            Usuario usuario
    ) {
        return manifestacaoRepository.findById(idManifestacao)
                .filter(manifestacao -> usuario.getIdEmpresa().equals(manifestacao.getIdEmpresa()));
    }

    private boolean ehSolicitante(Usuario usuario, Manifestacao manifestacao) {
        return Objects.equals(usuario.getIdUsuario(), manifestacao.getIdSolicitante());
    }

    private boolean ehEquipe(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .anyMatch(permissao -> PERFIS_EQUIPE.contains(permissao.getAuthority()));
    }

    private String autorExibicao(Proposta_solucao proposta, Manifestacao manifestacao) {
        boolean origemSolicitante = "SOLICITANTE".equals(proposta.getOrigemProposta());
        String rotuloPadrao = origemSolicitante
                ? "Proposta do solicitante"
                : "Autor não registrado";

        if (proposta.getIdAutor() == null
                || (origemSolicitante && manifestacao.getIdSolicitante() == null)) {
            return rotuloPadrao;
        }

        return usuarioRepository.findById(proposta.getIdAutor())
                .filter(usuario -> Objects.equals(usuario.getIdEmpresa(), manifestacao.getIdEmpresa()))
                .map(Usuario::getNomeUsuario)
                .filter(nome -> !nome.isBlank())
                .map(nome -> "Proposta de " + nome)
                .orElse(rotuloPadrao);
    }

    private boolean propostaFinalizada(Proposta_solucao proposta) {
        return "ACEITA".equalsIgnoreCase(proposta.getStatusProposta())
                || "RECUSADA".equalsIgnoreCase(proposta.getStatusProposta());
    }
}