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

    @Column(name = "nome_usuario", nullable = false, length = 150)
    private String nomeUsuario;

    @Column(name = "email_usuario", nullable = false, length = 255)
    private String emailUsuario;

    @Column(name = "senha_hash_usuario", length = 255)
    private String senhaHashUsuario;

    @Column(name = "perfil_usuario", length = 50)
    private String perfilUsuario;

    @Column(name = "is_ativo_usuario", nullable = false)
    private Boolean ativo;

    // Necessário para o JPA carregar os usuários do banco.
    protected Usuario() {
    }

    // Utilizado pelo cadastro público de clientes.
    public Usuario(
            Integer idEmpresa,
            String nomeUsuario,
            String emailUsuario,
            String senhaHashUsuario
    ) {
        this.idEmpresa = idEmpresa;
        this.nomeUsuario = nomeUsuario;
        this.emailUsuario = emailUsuario;
        this.senhaHashUsuario = senhaHashUsuario;
        this.perfilUsuario = "CLIENTE";
        this.ativo = true;
    }

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