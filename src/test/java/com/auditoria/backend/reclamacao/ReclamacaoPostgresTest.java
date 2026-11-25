package com.auditoria.backend.reclamacao;

import com.auditoria.backend.manifestacao.Manifestacao;
import com.auditoria.backend.manifestacao.ManifestacaoRepository;
import com.auditoria.backend.manifestacao.ProtocoloService;
import com.auditoria.backend.reclamacao.reclamacaoDTO.ReclamacaoFormDTO;
import com.auditoria.backend.reclamacao.reclamacaoService.ReclamacaoService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=${ODRCON_PROTOCOLO_TEST_DB_URL}",
        "spring.datasource.username=${ODRCON_PROTOCOLO_TEST_DB_USER:odrcon_teste}",
        "spring.datasource.password=${ODRCON_PROTOCOLO_TEST_DB_PASSWORD:}",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.hbm2ddl.create_namespaces=true",
        "spring.jpa.show-sql=false"
})
@DirtiesContext
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@EnabledIfEnvironmentVariable(named = "ODRCON_PROTOCOLO_TEST_DB_URL",
        matches = "jdbc:postgresql://(?:127\\.0\\.0\\.1|localhost):[0-9]+/[a-zA-Z0-9_-]+")
class ReclamacaoPostgresTest {

    private static final String SCHEMA = "protocolo_teste_" + UUID.randomUUID().toString().replace("-", "");
    private static final String LEGADO = "5ca9c1d4-d648-42ee-b9ff-7d77c086d32c";
    private static final String BASE = "/api/reclamacao";

    @Autowired WebApplicationContext contexto;
    @Autowired ManifestacaoRepository repository;
    @Autowired ReclamacaoService service;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean ProtocoloService gerador;

    private MockMvc mvc;

    @DynamicPropertySource
    static void configuraSchema(DynamicPropertyRegistry registry) {
        registry.add("spring.jpa.properties.hibernate.default_schema", () -> SCHEMA);
        registry.add("spring.datasource.hikari.schema", () -> SCHEMA);
    }

    @BeforeAll
    void preparaBanco() throws IOException {
        String script = Files.readString(Path.of("database/scripts/garantir_protocolo_unico.sql"));
        jdbc.execute(script);
        jdbc.execute("ALTER TABLE manifestacao RENAME CONSTRAINT uq_manifestacao_protocolo_unico TO uq_protocolo_existente");
        jdbc.execute(script);
        jdbc.execute("ALTER TABLE manifestacao ADD CONSTRAINT uq_descricao_teste UNIQUE (descricao_original)");
        jdbc.execute("ALTER TABLE manifestacao ADD CONSTRAINT ck_empresa_teste CHECK (id_empresa > 0)");
        jdbc.update("""
                INSERT INTO manifestacao (id_empresa, tipo, status, descricao_original, criado_em,
                    atualizado_em, protocolo_unico, is_anonimo, status_processamento)
                VALUES (3, 'RECLAMACAO', 'ABERTA', 'Relato legado', now(), now(), ?, true, 'PENDENTE')
                """, LEGADO);
        mvc = MockMvcBuilders.webAppContextSetup(contexto).build();
    }

    @Test
    void retornaProtocoloSomenteAposGravacaoEConsultaFormatosAntigoENovo() throws Exception {
        String novo = "738194205";
        when(gerador.gerar()).thenReturn(novo);

        enviar("Relato novo").andExpect(status().isCreated())
                .andExpect(content().json("""
                        {"mensagem":"Manifestação registrada com sucesso.","protocolo":"738194205"}
                        """, JsonCompareMode.STRICT));

        Manifestacao registrada = repository.findByProtocoloUnico(novo).orElseThrow();
        assertThat(registrada.getIdManifestacao()).isPositive();
        assertThat(registrada.getDescricaoOriginal()).isEqualTo("Relato novo");
        assertThat(registrada.getIdEmpresa()).isEqualTo(3);
        assertThat(registrada.getIdUnidade()).isNull();
        assertThat(registrada.getIdSolicitante()).isNull();
        assertThat(registrada.getAnonimo()).isTrue();
        assertThat(repository.findByProtocoloUnico(LEGADO)).isPresent();

        for (String codigo : Set.of(novo, LEGADO)) {
            mvc.perform(get(BASE + "/protocolo/{codigo}", codigo))
                    .andExpect(status().isOk())
                    .andExpect(content().string("Manifestação encontrada."));
        }
        mvc.perform(get(BASE + "/protocolo/{codigo}", "888888888")).andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM pg_constraint WHERE conrelid = 'manifestacao'::regclass
                  AND conname IN ('uq_protocolo_existente', 'uq_manifestacao_protocolo_unico')
                """, Integer.class)).isEqualTo(1);
    }

    @Test
    void requisicoesSimultaneasComColisaoPersistemProtocolosDistintos() throws Exception {
        String disputado = "294817630";
        String alternativo = "851307429";
        var chegadas = new CountDownLatch(2);
        var chamadas = new AtomicInteger();
        when(gerador.gerar()).thenAnswer(invocacao -> {
            if (chamadas.incrementAndGet() > 2) {
                return alternativo;
            }
            chegadas.countDown();
            assertThat(chegadas.await(10, TimeUnit.SECONDS)).isTrue();
            return disputado;
        });

        var executor = Executors.newFixedThreadPool(2);
        try {
            var primeira = executor.submit(() -> enviar("Primeiro relato concorrente").andExpect(status().isCreated())
                    .andReturn().getResponse().getContentAsString());
            var segunda = executor.submit(() -> enviar("Segundo relato concorrente").andExpect(status().isCreated())
                    .andReturn().getResponse().getContentAsString());
            assertThat(primeira.get(15, TimeUnit.SECONDS)).isNotEqualTo(segunda.get(15, TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
        }

        assertThat(repository.findAll()).filteredOn(m -> Set.of(disputado, alternativo).contains(m.getProtocoloUnico()))
                .extracting(Manifestacao::getProtocoloUnico).containsExactlyInAnyOrder(disputado, alternativo);
        verify(gerador, times(3)).gerar();
    }

    @Test
    void outraRestricaoUnicaERestricaoCheckNaoDisparamNovaTentativa() {
        when(gerador.gerar()).thenReturn("123456789", "987654321");

        assertThatThrownBy(() -> service.registrar(formulario("Relato legado", 3)))
                .isInstanceOf(DataIntegrityViolationException.class).hasRootCauseInstanceOf(SQLException.class)
                .rootCause().satisfies(erro -> {
                    assertThat(((SQLException) erro).getSQLState()).isEqualTo("23505");
                    assertThat(erro).hasMessageNotContaining("Relato legado");
                });
        verify(gerador).gerar();

        assertThatThrownBy(() -> service.registrar(formulario("Relato com empresa inválida", 0)))
                .isInstanceOf(DataIntegrityViolationException.class).hasRootCauseInstanceOf(SQLException.class)
                .rootCause().satisfies(erro -> {
                    assertThat(((SQLException) erro).getSQLState()).isEqualTo("23514");
                    assertThat(erro).hasMessageNotContaining("Relato com empresa inválida");
                });
        verify(gerador, times(2)).gerar();
    }

    private ResultActions enviar(String relato) throws Exception {
        return mvc.perform(multipart(BASE).param("reclamacao", relato).param("isAnonimo", "true")
                .param("idEmpresa", "3"));
    }

    private ReclamacaoFormDTO formulario(String relato, int empresa) {
        return new ReclamacaoFormDTO(true, relato, false, null, false, null, empresa, null);
    }
}
