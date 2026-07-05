package com.finanzas.api.transaccion.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

// Upsert payload for a monthly category budget. Re-sending the same categoria
// replaces its cap (one budget per user+category).
@Data
public class PresupuestoRegistroDTO {

    @NotNull(message = "{categoriaId.notnull}")
    private Long categoriaId;

    @NotNull(message = "{monto.notnull}")
    @DecimalMin(value = "0.01", message = "{monto.min}")
    private BigDecimal montoMensual;
}
