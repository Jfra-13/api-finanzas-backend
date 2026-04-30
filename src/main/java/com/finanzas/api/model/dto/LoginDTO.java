package com.finanzas.api.model.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LoginDTO {
    @Email(message = "{email.invalid}")
    @NotBlank(message = "{email.notblank}")
    private String email;

    @NotBlank(message = "{password.notblank}")
    private String password;
}