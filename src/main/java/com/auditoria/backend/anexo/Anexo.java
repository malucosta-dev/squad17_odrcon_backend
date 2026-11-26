package com.auditoria.backend.anexo;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/** Ficha de um arquivo anexado a uma manifestacao (tabela public.anexo). */
@Entity
@Table(name = "anexo", schema = "public")
public class Anexo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_anexo")
    private Integer idAnexo;

    @Column(name = "id_manifestacao", nullable = false)
    private Integer idManifestacao;

    @Column(name = "id_interacao")
    private Integer idInteracao;

    @Column(name = "tipo_anexo", nullable = false, length = 50)
    private String tipoAnexo;

    // Caminho do arquivo dentro da pasta de uploads (nao guarda o arquivo em si).
    @Column(name = "url_arquivo_anexo", nullable = false, columnDefinition = "text")
    private String urlArquivoAnexo;

    @Column(name = "transcricao_anexo", columnDefinition = "text")
    private String transcricaoAnexo;

    @Column(name = "criado_em_anexo", nullable = false, updatable = false)
    private LocalDateTime criadoEmAnexo;

    @Column(name = "status_processamento", nullable = false, length = 50)
    private String statusProcessamento;

    @Column(name = "resultado_moderacao", length = 50)
    private String resultadoModeracao;

    @PrePersist
    protected void onCreate() {
        if (this.criadoEmAnexo == null) {
            this.criadoEmAnexo = LocalDateTime.now();
        }
        if (this.statusProcessamento == null) {
            this.statusProcessamento = "PENDENTE";
        }
    }

    public Anexo() {
    }

    public Anexo(Integer idManifestacao, String tipoAnexo, String urlArquivoAnexo) {
        this.idManifestacao = idManifestacao;
        this.tipoAnexo = tipoAnexo;
        this.urlArquivoAnexo = urlArquivoAnexo;
    }

    public Integer getIdAnexo() { return idAnexo; }
    public Integer getIdManifestacao() { return idManifestacao; }
    public Integer getIdInteracao() { return idInteracao; }
    public String getTipoAnexo() { return tipoAnexo; }
    public String getUrlArquivoAnexo() { return urlArquivoAnexo; }
    public String getTranscricaoAnexo() { return transcricaoAnexo; }
    public LocalDateTime getCriadoEmAnexo() { return criadoEmAnexo; }
    public String getStatusProcessamento() { return statusProcessamento; }
    public String getResultadoModeracao() { return resultadoModeracao; }
}
