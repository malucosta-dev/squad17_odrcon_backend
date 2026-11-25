package com.auditoria.backend.reclamacao.reclamacaoController;

import com.auditoria.backend.manifestacao.ManifestacaoRepository;
import com.auditoria.backend.reclamacao.reclamacaoDTO.ReclamacaoDTO;
import com.auditoria.backend.reclamacao.reclamacaoDTO.ReclamacaoFormDTO;
import com.auditoria.backend.reclamacao.reclamacaoService.ReclamacaoService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reclamacao")
@CrossOrigin(origins = "http://localhost:5173")
public class ReclamacaoController {

    private final ManifestacaoRepository manifestacaoRepository;
    private final ReclamacaoService reclamacaoService;

    public ReclamacaoController(ManifestacaoRepository manifestacaoRepository, ReclamacaoService reclamacaoService) {
        this.manifestacaoRepository = manifestacaoRepository;
        this.reclamacaoService = reclamacaoService;
    }

    @PostMapping
    public ResponseEntity<ReclamacaoDTO> fazerReclamacao(@ModelAttribute ReclamacaoFormDTO form) {
        String protocolo = reclamacaoService.registrar(form);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new ReclamacaoDTO("Manifestação registrada com sucesso.", protocolo));
    }

    @GetMapping("/protocolo/{codigo}")
    public ResponseEntity<String> procurarReclamacao(@PathVariable String codigo) {
        if (manifestacaoRepository.findByProtocoloUnico(codigo).isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Protocolo não encontrado.");
        }

        return ResponseEntity.ok("Manifestação encontrada.");
    }
}
