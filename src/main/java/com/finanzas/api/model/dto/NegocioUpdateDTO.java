package com.finanzas.api.model.dto;

import com.finanzas.api.model.enums.TipoNegocio;
import com.finanzas.api.validation.ValueOfEnum;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class NegocioUpdateDTO {
    @NotBlank(message = "{tipoNegocio.notblank}")
    @ValueOfEnum(enumClass = TipoNegocio.class, message = "{tipoNegocio.invalid}")
    private String tipoNegocio;
}