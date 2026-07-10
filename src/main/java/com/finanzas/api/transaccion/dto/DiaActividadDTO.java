package com.finanzas.api.transaccion.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

// One calendar day with activity inside a month (resumen-diario). Days without
// movements are omitted on purpose: the client paints the calendar from this list.
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DiaActividadDTO {
    private LocalDate fecha;
    private BigDecimal ingresos;
    private BigDecimal egresos;
}
