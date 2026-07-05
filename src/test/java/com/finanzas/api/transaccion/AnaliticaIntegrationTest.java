package com.finanzas.api.transaccion;

import com.finanzas.api.support.IntegrationTestSupport;
import com.finanzas.api.transaccion.model.Categoria;
import com.finanzas.api.transaccion.model.TipoTransaccion;
import com.finanzas.api.usuario.model.Usuario;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AnaliticaIntegrationTest extends IntegrationTestSupport {

    private static final String CATEGORIAS = "/api/v1/finanzas/resumen-categorias";
    private static final String COMPARACION = "/api/v1/finanzas/analiticas/comparacion-categorias";
    private static final String TENDENCIA = "/api/v1/finanzas/tendencia-mensual";
    private static final String SALUD = "/api/v1/finanzas/salud-financiera";

    @Test
    void resumenCategorias_agrupaEgresosYBucketSinCategoria() throws Exception {
        Usuario usuario = crearUsuario();
        Categoria gasolina = crearCategoria("Gasolina", TipoTransaccion.EGRESO, null);
        Categoria alimentacion = crearCategoria("Alimentación", TipoTransaccion.EGRESO, null);
        LocalDateTime ahora = LocalDateTime.now();

        crearTransaccion(usuario, TipoTransaccion.EGRESO, "100.00", ahora, gasolina);
        crearTransaccion(usuario, TipoTransaccion.EGRESO, "50.00", ahora, gasolina);
        crearTransaccion(usuario, TipoTransaccion.EGRESO, "80.00", ahora, alimentacion);
        crearTransaccion(usuario, TipoTransaccion.EGRESO, "30.00", ahora, null); // uncategorized
        crearTransaccion(usuario, TipoTransaccion.INGRESO, "500.00", ahora, null); // income must be ignored

        mockMvc.perform(get(CATEGORIAS).header(AUTH, tokenDe(usuario)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("CATEGORY_SUMMARY_OK"))
                .andExpect(jsonPath("$.data['Gasolina']").value(150.00))
                .andExpect(jsonPath("$.data['Alimentación']").value(80.00))
                .andExpect(jsonPath("$.data['Sin categoría']").value(30.00));
    }

    @Test
    void resumenCategorias_conRangoAcotaAlPeriodoPedido() throws Exception {
        Usuario usuario = crearUsuario();
        Categoria gasolina = crearCategoria("Gasolina", TipoTransaccion.EGRESO, null);
        // One expense inside the requested range, one outside; only the first counts.
        crearTransaccion(usuario, TipoTransaccion.EGRESO, "100.00", LocalDate.of(2026, 6, 15).atTime(10, 0), gasolina);
        crearTransaccion(usuario, TipoTransaccion.EGRESO, "999.00", LocalDate.of(2026, 5, 15).atTime(10, 0), gasolina);

        mockMvc.perform(get(CATEGORIAS).header(AUTH, tokenDe(usuario))
                        .param("desde", "2026-06-01").param("hasta", "2026-06-30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data['Gasolina']").value(100.00));
    }

    @Test
    void resumenCategorias_rangoInvertido_devuelve400() throws Exception {
        Usuario usuario = crearUsuario();
        mockMvc.perform(get(CATEGORIAS).header(AUTH, tokenDe(usuario))
                        .param("desde", "2026-06-30").param("hasta", "2026-06-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("RANGO_FECHAS_INVALIDO"));
    }

    @Test
    void comparacionCategorias_periodoAnterior_calculaDeltasYReferencia() throws Exception {
        Usuario usuario = crearUsuario();
        Categoria gasolina = crearCategoria("Gasolina", TipoTransaccion.EGRESO, null);
        // Actual period 06-11..06-15 (5 days) → reference 06-06..06-10 (same length).
        crearTransaccion(usuario, TipoTransaccion.EGRESO, "320.00", LocalDate.of(2026, 6, 13).atTime(10, 0), gasolina);
        crearTransaccion(usuario, TipoTransaccion.EGRESO, "242.00", LocalDate.of(2026, 6, 8).atTime(10, 0), gasolina);

        mockMvc.perform(get(COMPARACION).header(AUTH, tokenDe(usuario))
                        .param("desde", "2026-06-11").param("hasta", "2026-06-15"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("CATEGORY_COMPARISON_OK"))
                .andExpect(jsonPath("$.data.periodoActual.desde").value("2026-06-11"))
                .andExpect(jsonPath("$.data.periodoAnterior.desde").value("2026-06-06"))
                .andExpect(jsonPath("$.data.periodoAnterior.hasta").value("2026-06-10"))
                .andExpect(jsonPath("$.data.categorias[?(@.categoria=='Gasolina')].deltaAbs").value(78.00))
                // (320 - 242) / 242 * 100 = 32.2, rounded HALF_UP to 1 decimal.
                .andExpect(jsonPath("$.data.categorias[?(@.categoria=='Gasolina')].deltaPct").value(32.2))
                .andExpect(jsonPath("$.data.totalActual").value(320.00))
                .andExpect(jsonPath("$.data.totalAnterior").value(242.00))
                .andExpect(jsonPath("$.data.totalDeltaPct").value(32.2));
    }

    @Test
    void comparacionCategorias_sinDatosDeReferencia_deltaPctNull() throws Exception {
        Usuario usuario = crearUsuario();
        Categoria gasolina = crearCategoria("Gasolina", TipoTransaccion.EGRESO, null);
        crearTransaccion(usuario, TipoTransaccion.EGRESO, "40.00", LocalDate.of(2026, 6, 13).atTime(10, 0), gasolina);
        // Reference window (06-06..06-10) is empty → division by zero avoided, delta null.

        mockMvc.perform(get(COMPARACION).header(AUTH, tokenDe(usuario))
                        .param("desde", "2026-06-11").param("hasta", "2026-06-15"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalAnterior").value(0))
                .andExpect(jsonPath("$.data.totalDeltaPct").value(nullValue()))
                .andExpect(jsonPath("$.data.categorias[?(@.categoria=='Gasolina')].anterior").value(0))
                .andExpect(jsonPath("$.data.categorias[?(@.categoria=='Gasolina')].deltaAbs").value(40.00));
    }

    @Test
    void comparacionCategorias_mismoPeriodoAnioAnterior() throws Exception {
        Usuario usuario = crearUsuario();
        Categoria gasolina = crearCategoria("Gasolina", TipoTransaccion.EGRESO, null);
        crearTransaccion(usuario, TipoTransaccion.EGRESO, "100.00", LocalDate.of(2026, 6, 13).atTime(10, 0), gasolina);
        crearTransaccion(usuario, TipoTransaccion.EGRESO, "50.00", LocalDate.of(2025, 6, 13).atTime(10, 0), gasolina);

        mockMvc.perform(get(COMPARACION).header(AUTH, tokenDe(usuario))
                        .param("desde", "2026-06-01").param("hasta", "2026-06-30")
                        .param("compararCon", "MISMO_PERIODO_ANIO_ANTERIOR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.periodoAnterior.desde").value("2025-06-01"))
                .andExpect(jsonPath("$.data.periodoAnterior.hasta").value("2025-06-30"))
                .andExpect(jsonPath("$.data.categorias[?(@.categoria=='Gasolina')].deltaPct").value(100.0));
    }

    @Test
    void comparacionCategorias_rangoInvertido_devuelve400() throws Exception {
        Usuario usuario = crearUsuario();
        mockMvc.perform(get(COMPARACION).header(AUTH, tokenDe(usuario))
                        .param("desde", "2026-06-30").param("hasta", "2026-06-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("RANGO_FECHAS_INVALIDO"));
    }

    @Test
    void tendenciaMensual_devuelveArraysParalelos() throws Exception {
        Usuario usuario = crearUsuario();
        LocalDateTime ahora = LocalDateTime.now();
        crearTransaccion(usuario, TipoTransaccion.INGRESO, "500.00", ahora, null);
        crearTransaccion(usuario, TipoTransaccion.EGRESO, "200.00", ahora, null);

        mockMvc.perform(get(TENDENCIA).header(AUTH, tokenDe(usuario)).param("meses", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.meses", hasSize(3)))
                .andExpect(jsonPath("$.data.ingresos", hasSize(3)))
                .andExpect(jsonPath("$.data.egresos", hasSize(3)))
                // Current month is last (oldest first).
                .andExpect(jsonPath("$.data.meses[2]").value(periodoActual()))
                .andExpect(jsonPath("$.data.ingresos[2]").value(500.00))
                .andExpect(jsonPath("$.data.egresos[2]").value(200.00));
    }

    @Test
    void saludFinanciera_reglaFelicitacionMetaCerca() throws Exception {
        Usuario usuario = crearUsuario();
        crearMeta(usuario, "1000", periodoActual(), "1,2,3,4,5,6,7");
        crearTransaccion(usuario, TipoTransaccion.INGRESO, "900.00", LocalDateTime.now(), null); // net 900 >= 80%

        mockMvc.perform(get(SALUD).header(AUTH, tokenDe(usuario)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.code=='META_CERCA')]", hasSize(1)))
                .andExpect(jsonPath("$.data[?(@.code=='META_CERCA')].tipo").value("FELICITACION"));
    }

    @Test
    void saludFinanciera_reglaGastoDiarioAlto() throws Exception {
        Usuario usuario = crearUsuario();
        crearMeta(usuario, "1000", periodoActual(), "1,2,3,4,5,6,7");
        LocalDateTime ahora = LocalDateTime.now();
        crearTransaccion(usuario, TipoTransaccion.INGRESO, "1200.00", ahora, null);
        crearTransaccion(usuario, TipoTransaccion.EGRESO, "1100.00", ahora, null); // today's expense beats the quota

        mockMvc.perform(get(SALUD).header(AUTH, tokenDe(usuario)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.code=='GASTO_DIARIO_ALTO')]", hasSize(1)));
    }

    @Test
    void saludFinanciera_reglaMetaEnRiesgo() throws Exception {
        Usuario usuario = crearUsuario();
        crearMeta(usuario, "100000", periodoActual(), "1,2,3,4,5,6,7");
        crearTransaccion(usuario, TipoTransaccion.INGRESO, "100.00", LocalDateTime.now(), null); // best day far below required quota

        mockMvc.perform(get(SALUD).header(AUTH, tokenDe(usuario)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.code=='META_EN_RIESGO')]", hasSize(1)));
    }
}
