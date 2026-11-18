
package com.auditoria.backend.auth;

// Dados que o frontend pode receber após o login.
// Nunca retornamos a senha ou seu hash.
public record LoginResponse(
    Integer idUsuario,
    String nome,
    Integer idEmpresa,
    String perfil
) {}
