package com.finanzas.api.transaccion.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

// Only the name is editable: changing the type of a category with history would
// silently corrupt past analytics (an EGRESO total would become an INGRESO one).
@Data
public class CategoriaUpdateDTO {
    @NotBlank(message = "{nombre.notblank}")
    private String nombre;
}
