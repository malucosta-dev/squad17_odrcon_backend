package com.auditoria.backend.manifestacao;

import jakarta.persistence.*;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

@Entity
@Table(name = "manifestacao")
public class Manifestacao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_manifestacao")
    private Integer idManifestacao;

    @Column(name = "id_unidade")
    private Integer idUnidade;

    @Column(name = "id_empresa", nullable = false)
    private Integer idEmpresa;

    @Column(name = "id_solicitante")
    private Integer idSolicitante;

    @Column(name = "id_responsavel")
    private Integer idResponsavel;

    @Column(name = "tipo", nullable = false, length = 50)
    private String tipo;

    @Column(name = "status", nullable = false, length = 50)
    private String status;

    @Column(name = "prioridade", length = 30)
    private String prioridade; // REVER

    @Column(name = "descricao_original", nullable = false, columnDefinition = "text")
    private String descricaoOriginal;

    @Column(name = "descricao_tratada", columnDefinition = "text")
    private String descricaoTratada;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "dados_estruturados")
    private String dadosEstruturados;

    @Column(name = "prazo_resposta")
    private LocalDateTime prazoResposta;

    @Column(name = "nivel_escalonamento")
    private Integer nivelEscalonamento;

    @Column(name = "avaliacao_resultado", length = 50)
    private String avaliacaoResultado;

    @Column(name = "avaliacao_nota")
    private Integer avaliacaoNota;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private LocalDateTime criadoEm;

    @Column(name = "atualizado_em", nullable = false)
    private LocalDateTime atualizadoEm;

    @Column(name = "canal", length = 50)
    private String canal;

    @Column(name = "protocolo_unico", nullable = false, length = 100)
    private String protocoloUnico;

    @Column(name = "is_anonimo", nullable = false)
    private Boolean anonimo;

    @Column(name = "status_processamento", nullable = false, length = 50)
    private String statusProcessamento;

    @Column(name = "nivel_gravidade", length = 30)
    private String nivelGravidade;

    // Cria e atualiza o datetime automaticamente
    @PrePersist
    protected void onCreate() {
        LocalDateTime agora = LocalDateTime.now();
        if (this.criadoEm == null) {
            this.criadoEm = agora;
        }
        if (this.atualizadoEm == null) {
            this.atualizadoEm = agora;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.atualizadoEm = LocalDateTime.now();
    }

    public Manifestacao() {
        // exigido pelo JPA
    }

    public Manifestacao(Integer idManifestacao, Integer idUnidade, Integer idEmpresa, Integer idSolicitante, Integer idResponsavel, String tipo, String status, String prioridade, String descricaoOriginal, String descricaoTratada, String dadosEstruturados, LocalDateTime prazoResposta, Integer nivelEscalonamento, String avaliacaoResultado, Integer avaliacaoNota, LocalDateTime criadoEm, LocalDateTime atualizadoEm, String canal, String protocoloUnico, Boolean anonimo, String statusProcessamento, String nivelGravidade) {
        this.idManifestacao = idManifestacao;
        this.idUnidade = idUnidade;
        this.idEmpresa = idEmpresa;
        this.idSolicitante = idSolicitante;
        this.idResponsavel = idResponsavel;
        this.tipo = tipo;
        this.status = status;
        this.prioridade = prioridade;
        this.descricaoOriginal = descricaoOriginal;
        this.descricaoTratada = descricaoTratada;
        this.dadosEstruturados = dadosEstruturados;
        this.prazoResposta = prazoResposta;
        this.nivelEscalonamento = nivelEscalonamento;
        this.avaliacaoResultado = avaliacaoResultado;
        this.avaliacaoNota = avaliacaoNota;
        this.criadoEm = criadoEm;
        this.atualizadoEm = atualizadoEm;
        this.canal = canal;
        this.protocoloUnico = protocoloUnico;
        this.anonimo = anonimo;
        this.statusProcessamento = statusProcessamento;
        this.nivelGravidade = nivelGravidade;
    }

    public Integer getIdManifestacao() { return idManifestacao; }
    public Integer getIdUnidade() { return idUnidade; }
    public Integer getIdEmpresa() { return idEmpresa; }
    public Integer getIdSolicitante() { return idSolicitante; }
    public Integer getIdResponsavel() { return idResponsavel; }
    public String getTipo() { return tipo; }
    public String getStatus() { return status; }
    public String getPrioridade() { return prioridade; }
    public String getDescricaoOriginal() { return descricaoOriginal; }
    public String getDescricaoTratada() { return descricaoTratada; }
    public String getDadosEstruturados() { return dadosEstruturados; }
    public LocalDateTime getPrazoResposta() { return prazoResposta; }
    public Integer getNivelEscalonamento() { return nivelEscalonamento; }
    public String getAvaliacaoResultado() { return avaliacaoResultado; }
    public Integer getAvaliacaoNota() { return avaliacaoNota; }
    public LocalDateTime getCriadoEm() { return criadoEm; }
    public LocalDateTime getAtualizadoEm() { return atualizadoEm; }
    public String getCanal() { return canal; }
    public String getProtocoloUnico() { return protocoloUnico; }
    public Boolean getAnonimo() { return anonimo; }
    public String getStatusProcessamento() { return statusProcessamento; }
    public String getNivelGravidade() { return nivelGravidade; }
}