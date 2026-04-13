package com.finanzas.api.model.dto;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class TransaccionRegistroDTO {
    private BigDecimal monto;
    private String tipo; // Esperaremos que diga "INGRESO" o "EGRESO"
    private Long usuarioId; // El ID del usuario que creamos hace un rato
}