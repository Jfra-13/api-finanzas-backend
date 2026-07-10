package com.finanzas.api.transaccion.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

// Aggregated income for one weekday over the requested window. 'dia' is an
// accent-free uppercase code (LUNES..DOMINGO) the client can switch on safely.
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DiaSemanaIngresoDTO {
    private String dia;
    private BigDecimal ingresos;
}
