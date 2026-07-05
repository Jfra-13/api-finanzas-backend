package com.finanzas.api.transaccion.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

// End-of-month projection from the current run-rate. Amounts are projected
// linearly by elapsed calendar days: proyectado = actual * diasDelMes / diasTranscurridos.
// brechaProyectada = utilidadProyectada - metaMensual (negative = below goal);
// enCamino is true when the projection reaches or beats the goal.
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ProyeccionMensualDTO {
    private String periodo; // "YYYY-MM"
    private int diasTranscurridos;
    private int diasDelMes;
    private int diasHabilesRestantes;
    private BigDecimal ingresoActual;
    private BigDecimal egresoActual;
    private BigDecimal utilidadActual;
    private BigDecimal ingresoProyectado;
    private BigDecimal egresoProyectado;
    private BigDecimal utilidadProyectada;
    private BigDecimal metaMensual;
    private BigDecimal brechaProyectada;
    private boolean enCamino;
}
