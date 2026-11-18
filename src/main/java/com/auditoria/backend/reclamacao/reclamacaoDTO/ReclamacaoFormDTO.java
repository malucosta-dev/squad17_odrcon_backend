package com.auditoria.backend.reclamacao.reclamacaoDTO;

import org.springframework.web.multipart.MultipartFile;

public record ReclamacaoFormDTO(Boolean isAnonimo, String reclamacao, Boolean hasSolucao, String solucao, Boolean hasAcordo, MultipartFile arquivo) {
}
