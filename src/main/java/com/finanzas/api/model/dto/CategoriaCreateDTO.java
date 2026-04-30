package com.finanzas.api.model.dto;

import com.finanzas.api.model.enums.TipoTransaccion;
import com.finanzas.api.validation.ValueOfEnum;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CategoriaCreateDTO {
    @NotBlank(message = "{nombre.notblank}")
    private String nombre;

    @NotBlank(message = "{tipo.notblank}")
    @ValueOfEnum(enumClass = TipoTransaccion.class, message = "{tipo.invalid}")
    private String tipo;
}
