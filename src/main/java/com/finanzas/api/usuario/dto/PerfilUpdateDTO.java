package com.finanzas.api.usuario.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

// Partial update: a null field means "keep the current value". Email is not
// editable here (it is the account identifier); tipoNegocio has its own endpoint.
@Data
public class PerfilUpdateDTO {
    @Size(max = 100, message = "{nombre.size}")
    private String nombre;

    @Size(max = 20, message = "{telefono.size}")
    private String telefono;
}
