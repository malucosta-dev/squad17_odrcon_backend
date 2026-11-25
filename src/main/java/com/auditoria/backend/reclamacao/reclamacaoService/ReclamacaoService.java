package com.auditoria.backend.reclamacao.reclamacaoService;

import com.auditoria.backend.manifestacao.ManifestacaoRepository;
import com.auditoria.backend.manifestacao.ProtocoloService;
import com.auditoria.backend.reclamacao.reclamacaoDTO.ReclamacaoFormDTO;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;

@Service
public class ReclamacaoService {

    private static final int MAX_TENTATIVAS = 5;

    private final ManifestacaoRepository manifestacaoRepository;
    private final ProtocoloService protocoloService;
    private final DuplicidadeService duplicidadeService;
    private final TransactionTemplate transacao;

    public ReclamacaoService(ManifestacaoRepository manifestacaoRepository, ProtocoloService protocoloService,
                             DuplicidadeService duplicidadeService, PlatformTransactionManager transactionManager) {
        this.manifestacaoRepository = manifestacaoRepository;
        this.protocoloService = protocoloService;
        this.duplicidadeService = duplicidadeService;
        this.transacao = new TransactionTemplate(transactionManager);
        this.transacao.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public String registrar(ReclamacaoFormDTO form) {
        return duplicidadeService.registrarSemDuplicidade(form.reclamacao(), () -> persistir(form));
    }

    private String persistir(ReclamacaoFormDTO form) {
        // Mantém a atribuição provisória usada no cadastro identificado.
        boolean identificado = Boolean.FALSE.equals(form.isAnonimo());
        Integer idEmpresa = identificado ? Integer.valueOf(1) : form.idEmpresa();
        Integer idUnidade = identificado ? null : form.idUnidade();
        Integer idSolicitante = identificado ? 1 : null;
        LocalDateTime agora = LocalDateTime.now();

        for (int tentativa = 0; tentativa < MAX_TENTATIVAS; tentativa++) {
            String protocolo = protocoloService.gerar();
            Integer inseridos = transacao.execute(status -> manifestacaoRepository.inserirReclamacao(
                    idUnidade, idEmpresa, idSolicitante, form.reclamacao(), agora, protocolo, form.isAnonimo()));
            if (Integer.valueOf(1).equals(inseridos)) {
                return protocolo;
            }
        }

        throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "Não foi possível gerar o protocolo. Tente novamente.");
    }
}
