package com.finanzas.api.usuario.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ForgotPasswordDTO {
    @Email(message = "{email.invalid}")
    @NotBlank(message = "{email.notblank}")
    private String email;
}