package com.finanzas.api.model.dto;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class ProgresoMetasDTO {
    // Progreso del Día
    private BigDecimal ingresoDiario;
    private BigDecimal metaDiaria;

    // Progreso de la Semana
    private BigDecimal ingresoSemanal;
    private BigDecimal metaSemanal;

    // Progreso del Mes
    private BigDecimal ingresoMensual;
    private BigDecimal metaMensual;
}