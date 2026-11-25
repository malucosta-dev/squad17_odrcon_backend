package com.auditoria.backend.reclamacao;

import com.auditoria.backend.manifestacao.ManifestacaoRepository;
import com.auditoria.backend.manifestacao.ProtocoloService;
import com.auditoria.backend.reclamacao.reclamacaoDTO.ReclamacaoFormDTO;
import com.auditoria.backend.reclamacao.reclamacaoService.DuplicidadeService;
import com.auditoria.backend.reclamacao.reclamacaoService.ReclamacaoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ReclamacaoServiceTest {

    private static final ReclamacaoFormDTO FORM = new ReclamacaoFormDTO(
            true, "Relato de teste", false, null, false, null, 3, null);

    private final ManifestacaoRepository repository = mock(ManifestacaoRepository.class);
    private final ProtocoloService gerador = mock(ProtocoloService.class);
    private final PlatformTransactionManager transacoes = mock(PlatformTransactionManager.class);
    private final ReclamacaoService service = new ReclamacaoService(repository, gerador,
            new DuplicidadeService(), transacoes);

    @BeforeEach
    void configuraTransacoes() {
        when(transacoes.getTransaction(argThat(definicao ->
                definicao.getPropagationBehavior() == TransactionDefinition.PROPAGATION_REQUIRES_NEW)))
                .thenAnswer(invocacao -> new SimpleTransactionStatus());
    }

    @Test
    void tentaNovoProtocoloEmOutraTransacaoEConfirmaAntesDeRetornar() {
        TransactionStatus primeira = new SimpleTransactionStatus();
        TransactionStatus segunda = new SimpleTransactionStatus();
        doReturn(primeira, segunda).when(transacoes).getTransaction(argThat(definicao ->
                definicao.getPropagationBehavior() == TransactionDefinition.PROPAGATION_REQUIRES_NEW));
        when(gerador.gerar()).thenReturn("738194205", "294817630");
        when(repository.inserirReclamacao(isNull(), eq(3), isNull(), eq(FORM.reclamacao()), any(),
                anyString(), eq(true))).thenReturn(0, 1);

        assertThat(service.registrar(FORM)).isEqualTo("294817630");

        InOrder ordem = inOrder(transacoes, repository);
        ordem.verify(transacoes).getTransaction(any());
        ordem.verify(repository).inserirReclamacao(isNull(), eq(3), isNull(), eq(FORM.reclamacao()),
                any(), eq("738194205"), eq(true));
        ordem.verify(transacoes).commit(primeira);
        ordem.verify(transacoes).getTransaction(any());
        ordem.verify(repository).inserirReclamacao(isNull(), eq(3), isNull(), eq(FORM.reclamacao()),
                any(), eq("294817630"), eq(true));
        ordem.verify(transacoes).commit(segunda);
        ordem.verifyNoMoreInteractions();
    }

    @Test
    void limitaTentativasELiberaRelatoAposFalha() {
        when(gerador.gerar()).thenReturn("738194205");

        assertThatThrownBy(() -> service.registrar(FORM))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        erro -> assertThat(erro.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
        verify(gerador, times(5)).gerar();
        verify(transacoes, times(5)).commit(any());

        when(repository.inserirReclamacao(isNull(), eq(3), isNull(), eq(FORM.reclamacao()), any(),
                anyString(), eq(true))).thenReturn(1);
        assertThat(service.registrar(FORM)).isEqualTo("738194205");
        assertThatThrownBy(() -> service.registrar(FORM))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        erro -> assertThat(erro.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
        verify(gerador, times(6)).gerar();
    }

    @Test
    void propagaOutrasViolacoesDeIntegridadeSemNovaTentativa() {
        var violacao = new DataIntegrityViolationException("id_empresa inválido");
        when(gerador.gerar()).thenReturn("738194205");
        when(repository.inserirReclamacao(isNull(), eq(3), isNull(), eq(FORM.reclamacao()), any(),
                anyString(), eq(true))).thenThrow(violacao);

        assertThatThrownBy(() -> service.registrar(FORM)).isSameAs(violacao);

        verify(gerador).gerar();
        verify(transacoes).rollback(any());
        verify(transacoes, never()).commit(any());
    }
}
