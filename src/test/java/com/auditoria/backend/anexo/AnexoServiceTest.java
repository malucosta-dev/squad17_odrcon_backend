package com.auditoria.backend.anexo;

import com.auditoria.backend.manifestacao.Manifestacao;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AnexoServiceTest {

    @TempDir
    Path pasta;

    AnexoRepository repository;
    AnexoService service;
    Manifestacao manifestacao;

    @BeforeEach
    void preparar() {
        repository = mock(AnexoRepository.class);
        when(repository.save(any(Anexo.class))).thenAnswer(i -> i.getArgument(0));
        service = new AnexoService(repository, pasta.toString(), 100);
        manifestacao = mock(Manifestacao.class);
        when(manifestacao.getIdManifestacao()).thenReturn(7);
    }

    private long arquivosNaPasta() throws Exception {
        try (Stream<Path> s = Files.walk(pasta)) {
            return s.filter(Files::isRegularFile).count();
        }
    }

    @Test
    void guardaArquivoComNomeAleatorioEAnotaNoBanco() throws Exception {
        var arq = new MockMultipartFile("arquivo", "Foto da Cozinha.PNG", "image/png", new byte[]{1, 2, 3});

        Anexo anexo = service.salvar(manifestacao, arq);

        assertEquals(7, anexo.getIdManifestacao());
        assertEquals("IMAGEM", anexo.getTipoAnexo());
        assertTrue(anexo.getUrlArquivoAnexo().matches("manifestacao-7/[0-9a-f-]{36}\\.png"));
        assertFalse(anexo.getUrlArquivoAnexo().contains("Cozinha"));
        assertArrayEquals(new byte[]{1, 2, 3}, Files.readAllBytes(pasta.resolve(anexo.getUrlArquivoAnexo())));
    }

    @Test
    void nomeMaliciosoNaoSaiDaPastaDeUploads() throws Exception {
        var arq = new MockMultipartFile("arquivo", "../../../etc/passwd.pdf", "application/pdf", new byte[]{1});

        Anexo anexo = service.salvar(manifestacao, arq);

        assertTrue(Files.exists(pasta.resolve(anexo.getUrlArquivoAnexo())));
        assertEquals(1, arquivosNaPasta());
    }

    @Test
    void semArquivoNaoFazNada() throws Exception {
        assertNull(service.salvar(manifestacao, null));
        assertNull(service.salvar(manifestacao, new MockMultipartFile("arquivo", new byte[0])));
        verify(repository, never()).save(any());
        assertEquals(0, arquivosNaPasta());
    }

    @Test
    void recusaTipoNaoPermitido() throws Exception {
        for (String nome : new String[]{"virus.exe", "script.sh", "semextensao", "dupla.pdf.exe"}) {
            var arq = new MockMultipartFile("arquivo", nome, "application/octet-stream", new byte[]{1});
            var erro = assertThrows(ResponseStatusException.class, () -> service.salvar(manifestacao, arq), nome);
            assertEquals(415, erro.getStatusCode().value(), nome);
        }
        verify(repository, never()).save(any());
        assertEquals(0, arquivosNaPasta());
    }

    @Test
    void recusaArquivoMaiorQueOLimite() {
        var arq = new MockMultipartFile("arquivo", "grande.pdf", "application/pdf", new byte[101]);
        var erro = assertThrows(ResponseStatusException.class, () -> service.validar(arq));
        assertEquals(413, erro.getStatusCode().value());
    }

    @Test
    void validarNaoGravaNada() throws Exception {
        service.validar(new MockMultipartFile("arquivo", "ok.pdf", "application/pdf", new byte[]{1}));
        verify(repository, never()).save(any());
        assertEquals(0, arquivosNaPasta());
    }

    @Test
    void apagaOArquivoSeOBancoFalhar() throws Exception {
        when(repository.save(any(Anexo.class))).thenThrow(new IllegalStateException("banco fora"));
        var arq = new MockMultipartFile("arquivo", "ok.pdf", "application/pdf", new byte[]{1});

        assertThrows(IllegalStateException.class, () -> service.salvar(manifestacao, arq));

        assertEquals(0, arquivosNaPasta());
    }
}
