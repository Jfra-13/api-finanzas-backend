package com.finanzas.api.model.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class TransaccionResponseDTO {
    private Long id;
    private BigDecimal monto;
    private String tipo;
    private String descripcion;
    private LocalDateTime fecha;
    private Long usuarioId;
}
