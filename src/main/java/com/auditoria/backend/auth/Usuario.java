
package com.auditoria.backend.auth;

import jakarta.persistence.*;

@Entity
@Table(name = "usuario")
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_usuario")
    private Integer idUsuario;

    @Column(name = "id_empresa", nullable = false)
    private Integer idEmpresa;

    @Column(name = "id_unidade")
    private Integer idUnidade;

    @Column(name = "nome_usuario", nullable = false)
    private String nomeUsuario;

    @Column(name = "email_usuario", nullable = false)
    private String emailUsuario;

    @Column(name = "senha_hash_usuario")
    private String senhaHashUsuario;

    @Column(name = "perfil_usuario")
    private String perfilUsuario;

    @Column(name = "is_ativo_usuario")
    private Boolean ativo;

    public Integer getIdUsuario() {
        return idUsuario;
    }

    public Integer getIdEmpresa() {
        return idEmpresa;
    }

    public Integer getIdUnidade() {
        return idUnidade;
    }

    public String getNomeUsuario() {
        return nomeUsuario;
    }

    public String getEmailUsuario() {
        return emailUsuario;
    }

    public String getSenhaHashUsuario() {
        return senhaHashUsuario;
    }

    public String getPerfilUsuario() {
        return perfilUsuario;
    }

    public Boolean getAtivo() {
        return ativo;
    }
}
