package com.finanzas.api.service;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.assertEquals;

class MetaServiceTest {

    // Verás MetaService en rojo porque aún no la hemos creado. ¡Es normal en TDD!
    private final MetaService metaService = new MetaService();

    @Test
    void dadoQueFaltanDiasYDinero_debeRetornarDivisionExacta() {
        BigDecimal meta = new BigDecimal("3000.00");
        BigDecimal utilidad = new BigDecimal("1500.00");
        int dias = 15;

        BigDecimal resultado = metaService.calcularCuotaDiaria(meta, utilidad, dias);

        assertEquals(new BigDecimal("100.00"), resultado);
    }

    @Test
    void dadoQueLaUtilidadSuperoLaMeta_debeRetornarCero() {
        BigDecimal resultado = metaService.calcularCuotaDiaria(
                new BigDecimal("3000.00"), new BigDecimal("3100.00"), 10);

        assertEquals(new BigDecimal("0.00"), resultado);
    }
}