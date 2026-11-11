package com.auditoria.backend.ia;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ReformulacaoRequest(

    @NotBlank(message = "O texto original da reclamação é obrigatório.")
    @Size(max = 5000, message = "O texto original deve ter no máximo 5000 caracteres.")
    String textoOriginal,

    @Size(max = 1000, message = "As instruções do revisor devem ter no máximo 1000 caracteres.")
    String instrucoesRevisor

) {}

