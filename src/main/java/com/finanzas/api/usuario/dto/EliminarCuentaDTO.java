package com.finanzas.api.usuario.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

// Account deletion asks for the password again: the JWT proves the session,
// the password proves the person at the device is the owner.
@Data
public class EliminarCuentaDTO {
    @NotBlank(message = "{password.notblank}")
    private String password;
}
