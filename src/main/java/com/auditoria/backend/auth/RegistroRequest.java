package com.auditoria.backend.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record RegistroRequest(

    @NotBlank(message = "O nome é obrigatório.")
    @Size(max = 150, message = "O nome deve ter até 150 caracteres.")
    String nome,

    @NotBlank(message = "O e-mail é obrigatório.")
    @Email(message = "Informe um e-mail válido.")
    @Size(max = 255, message = "O e-mail deve ter até 255 caracteres.")
    String email,

    @NotBlank(message = "A senha é obrigatória.")
    @Size(
        min = 8,
        max = 72,
        message = "A senha deve ter entre 8 e 72 caracteres."
    )
    String senha,

    @NotNull(message = "Selecione uma empresa.")
    @Positive(message = "Selecione uma empresa válida.")
    Integer idEmpresa

) {}