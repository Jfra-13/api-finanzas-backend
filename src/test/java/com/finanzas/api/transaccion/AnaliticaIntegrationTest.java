package com.finanzas.api.transaccion;

import com.finanzas.api.support.IntegrationTestSupport;
import com.finanzas.api.transaccion.model.Categoria;
import com.finanzas.api.transaccion.model.TipoTransaccion;
import com.finanzas.api.usuario.model.Usuario;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
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
    private static final String PROYECCION = "/api/v1/finanzas/proyeccion-mensual";
    private static final String TENDENCIA = "/api/v1/finanzas/tendencia-mensual";
    private static final String SALUD = "/api/v1/finanzas/salud-financiera";
    private static final String RESUMEN_DIARIO = "/api/v1/finanzas/resumen-diario";
    private static final String TENDENCIA_GRAN = "/api/v1/finanzas/tendencia";
    private static final String DIA_SEMANA = "/api/v1/finanzas/ingresos-por-dia-semana";

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
    void proyeccionMensual_calculaRunRateYBrecha() throws Exception {
        Usuario usuario = crearUsuario();
        crearMeta(usuario, "1000", periodoActual(), "1,2,3,4,5,6,7");
        crearTransaccion(usuario, TipoTransaccion.INGRESO, "800.00", LocalDateTime.now(), null);
        crearTransaccion(usuario, TipoTransaccion.EGRESO, "300.00", LocalDateTime.now(), null);

        // Projection is date-dependent, so mirror the impl formula off the real clock.
        int diasT = LocalDate.now().getDayOfMonth();
        int diasM = LocalDate.now().lengthOfMonth();
        BigDecimal ingresoProy = proyectar(new BigDecimal("800.00"), diasT, diasM);
        BigDecimal egresoProy = proyectar(new BigDecimal("300.00"), diasT, diasM);
        BigDecimal utilidadProy = ingresoProy.subtract(egresoProy);
        boolean enCamino = utilidadProy.compareTo(new BigDecimal("1000")) >= 0;

        mockMvc.perform(get(PROYECCION).header(AUTH, tokenDe(usuario)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("MONTHLY_PROJECTION_OK"))
                .andExpect(jsonPath("$.data.periodo").value(periodoActual()))
                .andExpect(jsonPath("$.data.diasTranscurridos").value(diasT))
                .andExpect(jsonPath("$.data.diasDelMes").value(diasM))
                .andExpect(jsonPath("$.data.ingresoActual").value(800.00))
                .andExpect(jsonPath("$.data.egresoActual").value(300.00))
                .andExpect(jsonPath("$.data.utilidadActual").value(500.00))
                .andExpect(jsonPath("$.data.metaMensual").value(1000.00))
                .andExpect(jsonPath("$.data.ingresoProyectado").value(ingresoProy.doubleValue()))
                .andExpect(jsonPath("$.data.egresoProyectado").value(egresoProy.doubleValue()))
                .andExpect(jsonPath("$.data.utilidadProyectada").value(utilidadProy.doubleValue()))
                .andExpect(jsonPath("$.data.brechaProyectada").value(utilidadProy.subtract(new BigDecimal("1000")).doubleValue()))
                .andExpect(jsonPath("$.data.enCamino").value(enCamino));
    }

    @Test
    void proyeccionMensual_metaInalcanzable_enCaminoFalse() throws Exception {
        Usuario usuario = crearUsuario();
        crearMeta(usuario, "1000000", periodoActual(), "1,2,3,4,5,6,7");
        crearTransaccion(usuario, TipoTransaccion.INGRESO, "500.00", LocalDateTime.now(), null);

        mockMvc.perform(get(PROYECCION).header(AUTH, tokenDe(usuario)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.enCamino").value(false));
    }

    @Test
    void proyeccionMensual_metaBaja_enCaminoTrue() throws Exception {
        Usuario usuario = crearUsuario();
        crearMeta(usuario, "1.00", periodoActual(), "1,2,3,4,5,6,7");
        crearTransaccion(usuario, TipoTransaccion.INGRESO, "500.00", LocalDateTime.now(), null);

        mockMvc.perform(get(PROYECCION).header(AUTH, tokenDe(usuario)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.enCamino").value(true));
    }

    @Test
    void proyeccionMensual_sinMeta_devuelve404() throws Exception {
        Usuario usuario = crearUsuario();
        mockMvc.perform(get(PROYECCION).header(AUTH, tokenDe(usuario)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("META_NO_ENCONTRADA"));
    }

    // Mirror of AnaliticaService.proyectar: linear run-rate, 2 decimals HALF_UP.
    private BigDecimal proyectar(BigDecimal actual, int diasTranscurridos, int diasDelMes) {
        return actual.multiply(BigDecimal.valueOf(diasDelMes))
                .divide(BigDecimal.valueOf(diasTranscurridos), 2, RoundingMode.HALF_UP);
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

    // Only days with movements come back, ascending, with both totals per day.
    @Test
    void resumenDiario_devuelveSoloDiasConActividad() throws Exception {
        Usuario usuario = crearUsuario();
        LocalDate dia1 = LocalDate.now().withDayOfMonth(1);
        LocalDate dia3 = LocalDate.now().withDayOfMonth(3);
        crearTransaccion(usuario, TipoTransaccion.INGRESO, "100.00", dia1.atTime(10, 0), null);
        crearTransaccion(usuario, TipoTransaccion.EGRESO, "40.00", dia1.atTime(15, 0), null);
        crearTransaccion(usuario, TipoTransaccion.INGRESO, "200.00", dia3.atTime(9, 0), null);

        mockMvc.perform(get(RESUMEN_DIARIO).header(AUTH, tokenDe(usuario))
                        .param("mes", periodoActual()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("DAILY_SUMMARY_OK"))
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.data[0].fecha").value(dia1.toString()))
                .andExpect(jsonPath("$.data[0].ingresos").value(100.00))
                .andExpect(jsonPath("$.data[0].egresos").value(40.00))
                .andExpect(jsonPath("$.data[1].fecha").value(dia3.toString()))
                .andExpect(jsonPath("$.data[1].ingresos").value(200.00));
    }

    @Test
    void resumenDiario_mesSinActividad_devuelveListaVacia() throws Exception {
        Usuario usuario = crearUsuario();

        mockMvc.perform(get(RESUMEN_DIARIO).header(AUTH, tokenDe(usuario)).param("mes", "2020-01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(0)));
    }

    @Test
    void resumenDiario_mesInvalido_devuelve400() throws Exception {
        Usuario usuario = crearUsuario();

        mockMvc.perform(get(RESUMEN_DIARIO).header(AUTH, tokenDe(usuario)).param("mes", "julio-2026"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PARAMETRO_INVALIDO"));
    }

    @Test
    void tendencia_granularidadMes_equivaleATendenciaMensual() throws Exception {
        Usuario usuario = crearUsuario();
        crearTransaccion(usuario, TipoTransaccion.INGRESO, "500.00", LocalDateTime.now(), null);

        mockMvc.perform(get(TENDENCIA_GRAN).header(AUTH, tokenDe(usuario))
                        .param("granularidad", "MES").param("ventana", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("TREND_OK"))
                .andExpect(jsonPath("$.data.periodos", hasSize(3)))
                .andExpect(jsonPath("$.data.periodos[2]").value(periodoActual()))
                .andExpect(jsonPath("$.data.ingresos[2]").value(500.00));
    }

    @Test
    void tendencia_granularidadSemana_rotulaConLunes() throws Exception {
        Usuario usuario = crearUsuario();
        crearTransaccion(usuario, TipoTransaccion.INGRESO, "300.00", LocalDateTime.now(), null);
        LocalDate lunesActual = LocalDate.now().with(
                java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY));

        mockMvc.perform(get(TENDENCIA_GRAN).header(AUTH, tokenDe(usuario))
                        .param("granularidad", "SEMANA").param("ventana", "4"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.periodos", hasSize(4)))
                // Current week is last and labeled with its Monday.
                .andExpect(jsonPath("$.data.periodos[3]").value(lunesActual.toString()))
                .andExpect(jsonPath("$.data.ingresos[3]").value(300.00));
    }

    @Test
    void tendencia_granularidadInvalida_devuelve400() throws Exception {
        Usuario usuario = crearUsuario();

        mockMvc.perform(get(TENDENCIA_GRAN).header(AUTH, tokenDe(usuario)).param("granularidad", "DIA"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PARAMETRO_INVALIDO"));
    }

    // Positional contract: always 7 items Monday-first, accent-free uppercase codes.
    @Test
    void ingresosPorDiaSemana_siempre7ItemsDesdeLunes() throws Exception {
        Usuario usuario = crearUsuario();
        LocalDate lunesActual = LocalDate.now().with(
                java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY));
        // Income on this week's Monday and on last week's Monday: both land in bucket 0.
        crearTransaccion(usuario, TipoTransaccion.INGRESO, "100.00", lunesActual.atTime(8, 0), null);
        crearTransaccion(usuario, TipoTransaccion.INGRESO, "150.00", lunesActual.minusWeeks(1).atTime(8, 0), null);
        // Expenses never count here.
        crearTransaccion(usuario, TipoTransaccion.EGRESO, "999.00", lunesActual.atTime(9, 0), null);

        mockMvc.perform(get(DIA_SEMANA).header(AUTH, tokenDe(usuario)).param("ventana", "4"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("WEEKDAY_INCOME_OK"))
                .andExpect(jsonPath("$.data", hasSize(7)))
                .andExpect(jsonPath("$.data[0].dia").value("LUNES"))
                .andExpect(jsonPath("$.data[0].ingresos").value(250.00))
                .andExpect(jsonPath("$.data[6].dia").value("DOMINGO"));
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

    // Health must keep working without a goal: quota rules are skipped, the rest run.
    @Test
    void saludFinanciera_sinMeta_evaluaReglasNoDependientesDeMeta() throws Exception {
        Usuario usuario = crearUsuario();
        LocalDateTime ahora = LocalDateTime.now();
        crearTransaccion(usuario, TipoTransaccion.INGRESO, "100.00", ahora, null);
        crearTransaccion(usuario, TipoTransaccion.EGRESO, "300.00", ahora, null); // expenses beat income

        mockMvc.perform(get(SALUD).header(AUTH, tokenDe(usuario)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.code=='EGRESOS_SUPERAN_INGRESOS')]", hasSize(1)));
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
