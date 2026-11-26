package com.auditoria.backend.solucao;

import com.auditoria.backend.manifestacao.Manifestacao;
import com.auditoria.backend.manifestacao.ManifestacaoRepository;
import com.auditoria.backend.solucao.Proposta_solucao;
import com.auditoria.backend.solucao.Proposta_solucaoRepository;
import com.auditoria.backend.solucao.solucaoDTO.CriarSolucaoDTO;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/solucoes")
@CrossOrigin(origins = "http://localhost:5173")
public class SolucaoController {

    @Autowired
    private Proposta_solucaoRepository solucaoRepository;

    @Autowired
    private ManifestacaoRepository manifestacaoRepository;

    // 1. POST: Cria uma nova proposta de solução para uma reclamação específica
    @PostMapping("/reclamacao/{idManifestacao}")
    @Transactional
    public ResponseEntity<String> criarProposta(
            @PathVariable Integer idManifestacao,
            @RequestBody CriarSolucaoDTO dto
    ) {
        // TODO: Futuramente identificar quem está propondo a solução (SOLICITANTE vs EMPRESA) via usuário autenticado

        // Regra 5: Validação de texto vazio
        if (dto.descricaoProposta() == null || dto.descricaoProposta().isBlank()) {
            return ResponseEntity.badRequest().body("A descrição da proposta é obrigatória.");
        }

        Optional<Manifestacao> manifestacaoOpt = manifestacaoRepository.findById(idManifestacao);
        if (manifestacaoOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Reclamação não encontrada.");
        }

        Manifestacao manifestacao = manifestacaoOpt.get();

        // Regra 4: Bloqueio de novas propostas se a reclamação já estiver RESOLVIDA
        if ("RESOLVIDA".equalsIgnoreCase(manifestacao.getStatus())) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body("Não é possível propor soluções para uma reclamação já resolvida.");
        }

        boolean desejaAcordo = Boolean.TRUE.equals(dto.desejaAcordo());

        Proposta_solucao novaProposta = new Proposta_solucao(
                null,                       // 1. idProposta (gerado pelo banco)
                manifestacao,               // 2. manifestacao (FK da reclamação)
                dto.descricaoProposta(),    // 3. descricaoProposta
                desejaAcordo,               // 4. desejaAcordo
                "PENDENTE",                 // 5. statusProposta
                null,                       // 6. criadoEm (@PrePersist preenche)
                null                        // 7. atualizadoEm (@PrePersist preenche)
        );

        solucaoRepository.save(novaProposta);

        return ResponseEntity.status(HttpStatus.CREATED).body("Proposta de solução criada com sucesso.");
    }

    // 2. PATCH: Recusa uma proposta de solução (válido tanto para solicitante quanto para empresa)
    @PatchMapping("/{idProposta}/recusar")
    @Transactional
    public ResponseEntity<String> recusarProposta(@PathVariable Integer idProposta) {
        Optional<Proposta_solucao> propostaOpt = solucaoRepository.findById(idProposta);
        if (propostaOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Proposta de solução não encontrada.");
        }

        Proposta_solucao proposta = propostaOpt.get();

        // Regra 1: Bloqueio de decisão repetida
        if ("ACEITA".equalsIgnoreCase(proposta.getStatusProposta()) || "RECUSADA".equalsIgnoreCase(proposta.getStatusProposta())) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body("Esta proposta já foi finalizada com status: " + proposta.getStatusProposta());
        }

        proposta.setStatusProposta("RECUSADA");
        solucaoRepository.save(proposta); // O @PreUpdate atualiza o atualizado_em automaticamente

        return ResponseEntity.ok("Proposta recusada com sucesso.");
    }

    // 3. PATCH: Aceita uma proposta de solução (válido tanto para solicitante quanto para empresa)
    @PatchMapping("/{idProposta}/aceitar")
    @Transactional
    public ResponseEntity<String> aceitarProposta(@PathVariable Integer idProposta) {
        Optional<Proposta_solucao> propostaOpt = solucaoRepository.findById(idProposta);
        if (propostaOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Proposta de solução não encontrada.");
        }

        Proposta_solucao proposta = propostaOpt.get();

        // Regra 1: Bloqueio de decisão repetida
        if ("ACEITA".equalsIgnoreCase(proposta.getStatusProposta()) || "RECUSADA".equalsIgnoreCase(proposta.getStatusProposta())) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body("Esta proposta já foi finalizada com status: " + proposta.getStatusProposta());
        }

        // 1. Aceita a proposta escolhida
        proposta.setStatusProposta("ACEITA");
        solucaoRepository.save(proposta);

        Manifestacao manifestacao = proposta.getManifestacao();

        // Regra 3: Encerra automaticamente outras propostas concorrentes em aberto da mesma manifestação como RECUSADA
        List<Proposta_solucao> outrasPropostas = solucaoRepository.findByManifestacao_IdManifestacao(manifestacao.getIdManifestacao());
        for (Proposta_solucao outra : outrasPropostas) {
            boolean isOutraProposta = !outra.getIdProposta().equals(proposta.getIdProposta());
            boolean isEmAberto = "PENDENTE".equalsIgnoreCase(outra.getStatusProposta())
                    || "AGUARDANDO_ACORDO".equalsIgnoreCase(outra.getStatusProposta());

            if (isOutraProposta && isEmAberto) {
                outra.setStatusProposta("RECUSADA");
                solucaoRepository.save(outra);
            }
        }

        // Regra 2: Efeito cascata na Manifestacao ao aceitar uma proposta
        manifestacao.setStatus(Boolean.TRUE.equals(proposta.getDesejaAcordo()) ? "EM_ACORDO" : "RESOLVIDA");
        manifestacaoRepository.save(manifestacao);

        return ResponseEntity.ok("Proposta aceita com sucesso.");
    }
}