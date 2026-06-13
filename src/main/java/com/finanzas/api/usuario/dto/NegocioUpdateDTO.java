package com.finanzas.api.usuario.dto;

import com.finanzas.api.usuario.model.TipoNegocio;
import com.finanzas.api.shared.validation.ValueOfEnum;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class NegocioUpdateDTO {
    @NotBlank(message = "{tipoNegocio.notblank}")
    @ValueOfEnum(enumClass = TipoNegocio.class, message = "{tipoNegocio.invalid}")
    private String tipoNegocio;
}
