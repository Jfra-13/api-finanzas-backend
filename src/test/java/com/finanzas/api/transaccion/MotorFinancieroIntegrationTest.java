package com.finanzas.api.transaccion;

import com.finanzas.api.support.IntegrationTestSupport;
import com.finanzas.api.transaccion.model.TipoTransaccion;
import com.finanzas.api.usuario.model.Usuario;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MotorFinancieroIntegrationTest extends IntegrationTestSupport {

    private static final String CUOTA = "/api/v1/finanzas/cuota-diaria";
    private static final String PROGRESO = "/api/v1/finanzas/progreso-metas";

    // (meta - utilidadNeta) / dias, with net = ingresos - egresos of the current month.
    @Test
    void cuotaDiaria_usaUtilidadNeta() throws Exception {
        Usuario usuario = crearUsuario();
        LocalDateTime ahora = LocalDateTime.now();
        crearTransaccion(usuario, TipoTransaccion.INGRESO, "2000.00", ahora, null);
        crearTransaccion(usuario, TipoTransaccion.EGRESO, "500.00", ahora, null); // net = 1500

        // (3000 - 1500) / 15 = 100.00
        mockMvc.perform(get(CUOTA).header(AUTH, tokenDe(usuario)).param("meta", "3000").param("dias", "15"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(100.00));
    }

    // The old lifetime bug: months other than the current one must not affect the quota.
    @Test
    void cuotaDiaria_ignoraMesesAnteriores() throws Exception {
        Usuario usuario = crearUsuario();
        LocalDateTime ahora = LocalDateTime.now();
        crearTransaccion(usuario, TipoTransaccion.INGRESO, "9999.00", ahora.minusMonths(1), null); // last month
        crearTransaccion(usuario, TipoTransaccion.INGRESO, "2000.00", ahora, null);
        crearTransaccion(usuario, TipoTransaccion.EGRESO, "500.00", ahora, null); // current net = 1500

        mockMvc.perform(get(CUOTA).header(AUTH, tokenDe(usuario)).param("meta", "3000").param("dias", "15"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(100.00));
    }

    // Net above the goal returns the negative surplus so the client can show "Superada".
    @Test
    void cuotaDiaria_metaSuperada_devuelveNegativo() throws Exception {
        Usuario usuario = crearUsuario();
        crearTransaccion(usuario, TipoTransaccion.INGRESO, "4000.00", LocalDateTime.now(), null);

        mockMvc.perform(get(CUOTA).header(AUTH, tokenDe(usuario)).param("meta", "3000").param("dias", "15"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(-1000.00));
    }

    // Indicators stay GROSS (income only); only the quota target uses net.
    @Test
    void progresoMetas_indicadoresEnBruto_motorEnNeto() throws Exception {
        Usuario usuario = crearUsuario();
        LocalDateTime ahora = LocalDateTime.now();
        crearTransaccion(usuario, TipoTransaccion.INGRESO, "1000.00", ahora, null);
        crearTransaccion(usuario, TipoTransaccion.EGRESO, "400.00", ahora, null); // net = 600

        mockMvc.perform(get(PROGRESO).header(AUTH, tokenDe(usuario)).param("meta", "3000").param("dias", "15"))
                .andExpect(status().isOk())
                // Gross: income only, expenses NOT subtracted from the indicator.
                .andExpect(jsonPath("$.data.ingresoDiario").value(1000.00))
                // Net engine: (3000 - 600) / 15 = 160.00
                .andExpect(jsonPath("$.data.metaDiaria").value(160.00))
                .andExpect(jsonPath("$.data.metaMensual").value(3000));
    }

    // Contract with the app: no ad-hoc params and no active goal is a 404, not a number.
    @Test
    void cuotaDiaria_sinMeta_devuelve404() throws Exception {
        Usuario usuario = crearUsuario();
        crearTransaccion(usuario, TipoTransaccion.INGRESO, "500.00", LocalDateTime.now(), null);

        mockMvc.perform(get(CUOTA).header(AUTH, tokenDe(usuario)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("META_NO_ENCONTRADA"));
    }

    @Test
    void progresoMetas_sinMeta_devuelve404() throws Exception {
        Usuario usuario = crearUsuario();

        mockMvc.perform(get(PROGRESO).header(AUTH, tokenDe(usuario)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("META_NO_ENCONTRADA"));
    }

    // Ad-hoc params still work without a persisted goal (no 404).
    @Test
    void cuotaDiaria_sinMetaPeroConParams_calculaNormal() throws Exception {
        Usuario usuario = crearUsuario();
        crearTransaccion(usuario, TipoTransaccion.INGRESO, "1500.00", LocalDateTime.now(), null);

        mockMvc.perform(get(CUOTA).header(AUTH, tokenDe(usuario)).param("meta", "3000").param("dias", "15"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(100.00));
    }

    // No query params: goal amount and working days come from the active Meta in the DB.
    @Test
    void cuotaDiaria_leeMetaYJornadaDesdeBD() throws Exception {
        Usuario usuario = crearUsuario();
        crearMeta(usuario, "3000", periodoActual(), "1,2,3,4,5,6,7"); // every day
        LocalDateTime ahora = LocalDateTime.now();
        crearTransaccion(usuario, TipoTransaccion.INGRESO, "2000.00", ahora, null);
        crearTransaccion(usuario, TipoTransaccion.EGRESO, "500.00", ahora, null); // net = 1500

        LocalDate hoy = LocalDate.now();
        int diasRestantes = hoy.lengthOfMonth() - hoy.getDayOfMonth() + 1; // every-day jornada
        BigDecimal esperado = new BigDecimal("1500").divide(BigDecimal.valueOf(diasRestantes), 2, RoundingMode.HALF_UP);

        mockMvc.perform(get(CUOTA).header(AUTH, tokenDe(usuario)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(esperado.doubleValue()));
    }
}
