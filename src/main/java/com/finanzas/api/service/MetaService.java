package com.finanzas.api.service;

import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
public class MetaService {

    public BigDecimal calcularCuotaDiaria(BigDecimal metaMensual, BigDecimal utilidadActual, int diasRestantes) {
        if (utilidadActual.compareTo(metaMensual) >= 0) {
            return new BigDecimal("0.00");
        }

        BigDecimal faltante = metaMensual.subtract(utilidadActual);

        if (diasRestantes <= 0) {
            return faltante;
        }

        // Dividimos el faltante entre los días y redondeamos a 2 decimales para monedas
        return faltante.divide(BigDecimal.valueOf(diasRestantes), 2, RoundingMode.HALF_UP);
    }
}