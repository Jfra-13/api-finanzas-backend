package com.finanzas.api.meta.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class MetaResponseDTO {
    private Long id;
    private BigDecimal montoObjetivo;
    private String periodo;
    private List<Integer> diasLaborables;
    private boolean activa;
}
