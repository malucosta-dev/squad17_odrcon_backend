package com.auditoria.backend.usuario.usuarioDTO;

import java.time.LocalDateTime;

public record UsuarioDTO(Integer idUsuario, Integer idUnidade, Integer idEmpresa, String nomeUsuario, String emailUsuario, String telefoneUsuario, String senhaUsuario, String perfilUsuario, Boolean isAtivoUsuario, LocalDateTime criadoEmUsuario) {
}
