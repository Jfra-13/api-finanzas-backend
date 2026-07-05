package com.finanzas.api.transaccion.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

// A budget plus its current-month status. 'restante' goes negative and 'excedido'
// turns true once the spend passes the cap; 'consumoPct' has no upper bound.
@Data
@AllArgsConstructor
@NoArgsConstructor
public class PresupuestoResponseDTO {
    private Long id;
    private Long categoriaId;
    private String categoriaNombre;
    private BigDecimal montoMensual;
    private BigDecimal gastadoMes;
    private BigDecimal restante;
    private BigDecimal consumoPct;
    private boolean excedido;
}
