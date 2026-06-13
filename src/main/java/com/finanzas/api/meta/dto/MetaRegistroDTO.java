package com.finanzas.api.meta.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class MetaRegistroDTO {

    @NotNull(message = "{meta.monto.notnull}")
    @DecimalMin(value = "0.01", message = "{meta.monto.min}")
    private BigDecimal montoObjetivo;

    // Working days the user plans to work this month: 1=Monday .. 7=Sunday.
    @NotEmpty(message = "{meta.dias.notempty}")
    private List<@NotNull @Min(value = 1, message = "{meta.dias.range}")
                 @Max(value = 7, message = "{meta.dias.range}") Integer> diasLaborables;
}
