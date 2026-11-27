package com.auditoria.backend.solucao;

import com.auditoria.backend.manifestacao.Manifestacao;
import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "proposta_solucao",
        schema = "public",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_proposta_manifestacao_termo",
                columnNames = {"id_proposta", "id_manifestacao"}
        )
)
public class Proposta_solucao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_proposta")
    private Integer idProposta;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "id_manifestacao",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_proposta_manifestacao")
    )
    private Manifestacao manifestacao;

    @Column(name = "id_autor")
    private Integer idAutor;

    @Column(name = "origem_proposta", length = 20)
    private String origemProposta;

    @Column(name = "descricao_proposta", columnDefinition = "TEXT", nullable = false)
    private String descricaoProposta;

    @Column(name = "deseja_acordo")
    private Boolean desejaAcordo;

    @Column(name = "status_proposta", length = 50, nullable = false)
    private String statusProposta = "PENDENTE";

    @Column(name = "criado_em", nullable = false, updatable = false)
    private LocalDateTime criadoEm;

    @Column(name = "atualizado_em", nullable = false)
    private LocalDateTime atualizadoEm;

    @PrePersist
    protected void onCreate() {
        LocalDateTime agora = LocalDateTime.now();

        if (criadoEm == null) {
            criadoEm = agora;
        }
        if (atualizadoEm == null) {
            atualizadoEm = agora;
        }
        if (statusProposta == null || statusProposta.isBlank()) {
            statusProposta = "PENDENTE";
        }
    }

    @PreUpdate
    protected void onUpdate() {
        atualizadoEm = LocalDateTime.now();
    }

    public Proposta_solucao() {
    }

    public Proposta_solucao(
            Integer idProposta,
            Manifestacao manifestacao,
            String descricaoProposta,
            Boolean desejaAcordo,
            String statusProposta,
            LocalDateTime criadoEm,
            LocalDateTime atualizadoEm
    ) {
        this.idProposta = idProposta;
        this.manifestacao = manifestacao;
        this.descricaoProposta = descricaoProposta;
        this.desejaAcordo = desejaAcordo;
        this.statusProposta = statusProposta;
        this.criadoEm = criadoEm;
        this.atualizadoEm = atualizadoEm;
    }

    public Integer getIdProposta() {
        return idProposta;
    }

    public void setIdProposta(Integer idProposta) {
        this.idProposta = idProposta;
    }

    public Manifestacao getManifestacao() {
        return manifestacao;
    }

    public void setManifestacao(Manifestacao manifestacao) {
        this.manifestacao = manifestacao;
    }

    public Integer getIdAutor() {
        return idAutor;
    }

    public String getOrigemProposta() {
        return origemProposta;
    }

    public void registrarAutoria(Integer idAutor, String origemProposta) {
        this.idAutor = idAutor;
        this.origemProposta = origemProposta;
    }

    public String getDescricaoProposta() {
        return descricaoProposta;
    }

    public void setDescricaoProposta(String descricaoProposta) {
        this.descricaoProposta = descricaoProposta;
    }

    public Boolean getDesejaAcordo() {
        return desejaAcordo;
    }

    public void setDesejaAcordo(Boolean desejaAcordo) {
        this.desejaAcordo = desejaAcordo;
    }

    public String getStatusProposta() {
        return statusProposta;
    }

    public void setStatusProposta(String statusProposta) {
        this.statusProposta = statusProposta;
    }

    public LocalDateTime getCriadoEm() {
        return criadoEm;
    }

    public void setCriadoEm(LocalDateTime criadoEm) {
        this.criadoEm = criadoEm;
    }

    public LocalDateTime getAtualizadoEm() {
        return atualizadoEm;
    }

    public void setAtualizadoEm(LocalDateTime atualizadoEm) {
        this.atualizadoEm = atualizadoEm;
    }
}