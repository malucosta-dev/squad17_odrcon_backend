package com.auditoria.backend.reclamacao;

import com.auditoria.backend.anexo.Anexo;
import com.auditoria.backend.anexo.AnexoRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Passa pelo controller de verdade e grava no banco Neon, mas dentro de uma
 * transacao de teste que e DESFEITA no fim: nada fica salvo.
 * Empresa 3 / unidade 1 sao os dados de teste existentes no banco.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = "odrcon.uploads.dir=target/test-uploads")
class ReclamacaoAnexoIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    AnexoRepository anexoRepository;

    @Value("${odrcon.uploads.dir}")
    String pastaUploads;

    private MockMultipartHttpServletRequestBuilder reclamacao(String texto) {
        return (MockMultipartHttpServletRequestBuilder) multipart("/api/reclamacao")
                .param("isAnonimo", "true")
                .param("reclamacao", texto)
                .param("hasSolucao", "false")
                .param("solucao", "")
                .param("hasAcordo", "false")
                .param("idEmpresa", "3")
                .param("idUnidade", "1");
    }

    private String textoUnico() {
        return "Teste automatico de anexo " + UUID.randomUUID();
    }

    @Test
    void reclamacaoComArquivoCriaLinhaEmAnexoEGuardaOArquivo() throws Exception {
        long antes = anexoRepository.count();
        var pdf = new MockMultipartFile("arquivo", "prova.pdf", "application/pdf", "%PDF-1.4 teste".getBytes());

        mvc.perform(reclamacao(textoUnico()).file(pdf)).andExpect(status().isCreated());
        anexoRepository.flush();

        assertEquals(antes + 1, anexoRepository.count());
        List<Anexo> todos = anexoRepository.findAll();
        Anexo novo = todos.get(todos.size() - 1);
        assertEquals("DOCUMENTO", novo.getTipoAnexo());
        assertEquals("PENDENTE", novo.getStatusProcessamento());
        assertNotNull(novo.getCriadoEmAnexo());
        Path guardado = Path.of(pastaUploads).toAbsolutePath().resolve(novo.getUrlArquivoAnexo());
        assertTrue(Files.exists(guardado), "arquivo deveria existir em " + guardado);
        assertEquals("%PDF-1.4 teste", Files.readString(guardado));
    }

    @Test
    void reclamacaoSemArquivoContinuaFuncionando() throws Exception {
        long antes = anexoRepository.count();

        mvc.perform(reclamacao(textoUnico())).andExpect(status().isCreated());
        anexoRepository.flush();

        assertEquals(antes, anexoRepository.count());
    }

    @Test
    void arquivoProibidoDa415ENaoQueimaOTextoDaReclamacao() throws Exception {
        String texto = textoUnico();
        var exe = new MockMultipartFile("arquivo", "virus.exe", "application/octet-stream", new byte[]{1});
        var pdf = new MockMultipartFile("arquivo", "ok.pdf", "application/pdf", new byte[]{1});

        mvc.perform(reclamacao(texto).file(exe)).andExpect(status().isUnsupportedMediaType());
        // Mesmo texto, agora com arquivo valido: nao pode dar 409 (duplicada).
        mvc.perform(reclamacao(texto).file(pdf)).andExpect(status().isCreated());
    }

    @Test
    void anonimoComSoEmpresaSemUnidadeFunciona() throws Exception {
        mvc.perform(multipart("/api/reclamacao")
                .param("isAnonimo", "true").param("reclamacao", textoUnico())
                .param("hasSolucao", "false").param("solucao", "").param("hasAcordo", "false")
                .param("idEmpresa", "1"))
                .andExpect(status().isCreated());
    }

    @Test
    void unidadeDeOutraEmpresaEhRecusadaPeloBanco() {
        // Unidade 1 pertence a empresa 3, nao a 1: o banco (FK composta) barra o par.
        assertThrows(Exception.class, () -> mvc.perform(multipart("/api/reclamacao")
                .param("isAnonimo", "true").param("reclamacao", textoUnico())
                .param("hasSolucao", "false").param("solucao", "").param("hasAcordo", "false")
                .param("idEmpresa", "1").param("idUnidade", "1")));
    }
}
