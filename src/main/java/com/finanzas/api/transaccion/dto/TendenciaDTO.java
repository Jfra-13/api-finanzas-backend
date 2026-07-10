package com.finanzas.api.transaccion.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

// Parallel arrays, oldest first, same length. Period labels: "yyyy-MM" for
// monthly granularity, "yyyy-MM-dd" (the Monday starting the week) for weekly.
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TendenciaDTO {
    private List<String> periodos;
    private List<BigDecimal> ingresos;
    private List<BigDecimal> egresos;
}
