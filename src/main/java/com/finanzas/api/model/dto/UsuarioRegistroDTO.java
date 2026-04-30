package com.finanzas.api.model.dto;

import com.finanzas.api.model.enums.TipoNegocio;
import com.finanzas.api.validation.ValueOfEnum;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UsuarioRegistroDTO {
    @NotBlank(message = "{nombre.notblank}")
    private String nombre;

    @NotBlank(message = "{email.notblank}")
    @Email(message = "{email.invalid}")
    private String email;

    @NotBlank(message = "{password.notblank}")
    @Size(min = 8, max = 72, message = "{password.size}")
    @Pattern(regexp = "^(?=.*[0-9])(?=.*[a-zA-Z]).*$", message = "{password.pattern}")
    private String password;

    @ValueOfEnum(enumClass = TipoNegocio.class, message = "{tipoNegocio.invalid}")
    private String tipoNegocio;
}