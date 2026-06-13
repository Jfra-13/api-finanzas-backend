package com.finanzas.api.transaccion.dto;

import com.finanzas.api.transaccion.model.TipoTransaccion;
import com.finanzas.api.shared.validation.ValueOfEnum;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class TransaccionRegistroDTO {
    @NotNull(message = "{monto.notnull}")
    @DecimalMin(value = "0.01", message = "{monto.min}")
    private BigDecimal monto;

    @NotNull(message = "{tipo.notnull}")
    @ValueOfEnum(enumClass = TipoTransaccion.class, message = "{tipo.invalid}")
    private String tipo;

    @Size(max = 500, message = "{descripcion.size}")
    private String descripcion;

    private LocalDateTime fecha;
}
