package com.finanzas.api.transaccion.dto;

import com.finanzas.api.transaccion.model.TipoTransaccion;
import com.finanzas.api.shared.validation.ValueOfEnum;
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
