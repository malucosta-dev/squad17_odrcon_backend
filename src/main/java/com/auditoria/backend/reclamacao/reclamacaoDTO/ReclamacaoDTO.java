package com.auditoria.backend.reclamacao.reclamacaoDTO;

import com.auditoria.backend.usuario.usuarioDTO.UsuarioDTO;

public record ReclamacaoDTO(UsuarioDTO usuario, ReclamacaoFormDTO reclamacao) {
}
