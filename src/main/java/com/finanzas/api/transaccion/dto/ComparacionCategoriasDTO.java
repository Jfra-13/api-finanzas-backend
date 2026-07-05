package com.finanzas.api.transaccion.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

// Per-category expense comparison between the requested period and a reference
// period (immediately preceding same-length window, or the same dates a year ago).
// deltaPct is null when the reference amount is 0 (the client renders "nuevo").
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ComparacionCategoriasDTO {

    private Periodo periodoActual;
    private Periodo periodoAnterior;
    private List<CategoriaDelta> categorias;
    private BigDecimal totalActual;
    private BigDecimal totalAnterior;
    private BigDecimal totalDeltaPct;

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Periodo {
        private LocalDate desde; // inclusive
        private LocalDate hasta;  // inclusive
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class CategoriaDelta {
        private String categoria;
        private BigDecimal actual;
        private BigDecimal anterior;
        private BigDecimal deltaAbs;
        private BigDecimal deltaPct; // null when 'anterior' is 0
    }
}
