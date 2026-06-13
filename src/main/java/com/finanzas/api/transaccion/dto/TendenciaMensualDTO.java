package com.finanzas.api.transaccion.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

// Cash-flow line chart: parallel arrays indexed by month, oldest first.
@Data
@AllArgsConstructor
@NoArgsConstructor
public class TendenciaMensualDTO {
    private List<String> meses;       // "YYYY-MM" labels
    private List<BigDecimal> ingresos;
    private List<BigDecimal> egresos;
}
