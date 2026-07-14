package com.finanzas.api.meta.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

// One month of goal history: the registered goal versus the net profit actually
// achieved that month. 'utilidadReal' is computed on-the-fly (ingresos - egresos),
// nothing is materialized; 'cumplida' is utilidadReal >= metaMensual.
@Data
@AllArgsConstructor
@NoArgsConstructor
public class MetaHistorialDTO {
    private String periodo; // "YYYY-MM"
    private BigDecimal metaMensual;
    private BigDecimal utilidadReal;
    private boolean cumplida;
}
