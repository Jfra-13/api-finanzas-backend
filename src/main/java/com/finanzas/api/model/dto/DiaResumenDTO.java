package com.finanzas.api.model.dto;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class DiaResumenDTO {
    private String dia;
    private BigDecimal ingresos;
    private BigDecimal egresos;

    public DiaResumenDTO(String dia, BigDecimal ingresos, BigDecimal egresos) {
        this.dia = dia;
        this.ingresos = ingresos;
        this.egresos = egresos;
    }
}