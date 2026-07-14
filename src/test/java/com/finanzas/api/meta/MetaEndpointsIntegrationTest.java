package com.finanzas.api.meta;

import com.finanzas.api.support.IntegrationTestSupport;
import com.finanzas.api.usuario.model.Usuario;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MetaEndpointsIntegrationTest extends IntegrationTestSupport {

    private static final String METAS = "/api/v1/finanzas/metas";

    @Test
    void fijarMeta_creaYDevuelveEnvelope() throws Exception {
        Usuario usuario = crearUsuario();
        String body = "{\"montoObjetivo\":3000.00,\"diasLaborables\":[1,2,3,4,5]}";

        mockMvc.perform(post(METAS).header(AUTH, tokenDe(usuario)).contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("GOAL_SET"))
                .andExpect(jsonPath("$.data.montoObjetivo").value(3000.00))
                .andExpect(jsonPath("$.data.periodo").value(periodoActual()))
                .andExpect(jsonPath("$.data.diasLaborables", hasSize(5)))
                .andExpect(jsonPath("$.data.activa").value(true));
    }

    @Test
    void fijarMeta_segundaVez_haceUpsertNoDuplica() throws Exception {
        Usuario usuario = crearUsuario();
        String token = tokenDe(usuario);

        mockMvc.perform(post(METAS).header(AUTH, token).contentType(APPLICATION_JSON)
                .content("{\"montoObjetivo\":3000.00,\"diasLaborables\":[1,2,3,4,5]}")).andExpect(status().isOk());
        mockMvc.perform(post(METAS).header(AUTH, token).contentType(APPLICATION_JSON)
                .content("{\"montoObjetivo\":5000.00,\"diasLaborables\":[6,7]}")).andExpect(status().isOk());

        // GET /actual reflects the latest values...
        mockMvc.perform(get(METAS + "/actual").header(AUTH, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.montoObjetivo").value(5000.00))
                .andExpect(jsonPath("$.data.diasLaborables", hasSize(2)));

        // ...and only one goal row exists for this user/period.
        long activas = metaRepository.findAll().stream()
                .filter(m -> m.getUsuario().getId().equals(usuario.getId()) && m.isActiva())
                .count();
        org.junit.jupiter.api.Assertions.assertEquals(1, activas);
    }

    @Test
    void getActual_sinMeta_devuelve404() throws Exception {
        Usuario usuario = crearUsuario();
        mockMvc.perform(get(METAS + "/actual").header(AUTH, tokenDe(usuario)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("META_NO_ENCONTRADA"));
    }

    @Test
    void fijarMeta_diaFueraDeRango_devuelve400() throws Exception {
        Usuario usuario = crearUsuario();
        mockMvc.perform(post(METAS).header(AUTH, tokenDe(usuario)).contentType(APPLICATION_JSON)
                        .content("{\"montoObjetivo\":3000.00,\"diasLaborables\":[8]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void fijarMeta_diasVacios_devuelve400() throws Exception {
        Usuario usuario = crearUsuario();
        mockMvc.perform(post(METAS).header(AUTH, tokenDe(usuario)).contentType(APPLICATION_JSON)
                        .content("{\"montoObjetivo\":3000.00,\"diasLaborables\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void historial_calculaUtilidadNetaYCumplidaPorMes() throws Exception {
        Usuario usuario = crearUsuario();
        java.time.YearMonth mesPasado = java.time.YearMonth.now().minusMonths(1);
        crearMeta(usuario, "1000.00", mesPasado.toString(), "1,2,3,4,5");
        crearMeta(usuario, "5000.00", periodoActual(), "1,2,3,4,5");
        // Last month: net profit 1500 - 300 = 1200 >= 1000 (met).
        crearTransaccion(usuario, com.finanzas.api.transaccion.model.TipoTransaccion.INGRESO, "1500.00",
                mesPasado.atDay(10).atStartOfDay(), null);
        crearTransaccion(usuario, com.finanzas.api.transaccion.model.TipoTransaccion.EGRESO, "300.00",
                mesPasado.atDay(15).atStartOfDay(), null);
        // Current month: 100 month-to-date, far from 5000 (not met).
        crearTransaccion(usuario, com.finanzas.api.transaccion.model.TipoTransaccion.INGRESO, "100.00",
                java.time.LocalDateTime.now(), null);

        mockMvc.perform(get(METAS + "/historial").header(AUTH, tokenDe(usuario)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("GOALS_HISTORY_OK"))
                .andExpect(jsonPath("$.data", hasSize(2)))
                // Oldest first.
                .andExpect(jsonPath("$.data[0].periodo").value(mesPasado.toString()))
                .andExpect(jsonPath("$.data[0].utilidadReal").value(1200.00))
                .andExpect(jsonPath("$.data[0].cumplida").value(true))
                .andExpect(jsonPath("$.data[1].periodo").value(periodoActual()))
                .andExpect(jsonPath("$.data[1].cumplida").value(false));
    }

    @Test
    void historial_sinMetas_devuelveListaVacia() throws Exception {
        Usuario usuario = crearUsuario();
        mockMvc.perform(get(METAS + "/historial").header(AUTH, tokenDe(usuario)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("GOALS_HISTORY_OK"))
                .andExpect(jsonPath("$.data", hasSize(0)));
    }

    @Test
    void historial_respetaVentanaDeMeses() throws Exception {
        Usuario usuario = crearUsuario();
        java.time.YearMonth haceDosMeses = java.time.YearMonth.now().minusMonths(2);
        crearMeta(usuario, "1000.00", haceDosMeses.toString(), "1,2,3,4,5");

        // Window of 1 month (current only) leaves the old goal out...
        mockMvc.perform(get(METAS + "/historial").header(AUTH, tokenDe(usuario)).param("meses", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(0)));

        // ...a window of 3 months reaches it.
        mockMvc.perform(get(METAS + "/historial").header(AUTH, tokenDe(usuario)).param("meses", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].periodo").value(haceDosMeses.toString()));
    }

    @Test
    void fijarMeta_sinToken_devuelve401() throws Exception {
        mockMvc.perform(post(METAS).contentType(APPLICATION_JSON)
                        .content("{\"montoObjetivo\":3000.00,\"diasLaborables\":[1,2,3]}"))
                .andExpect(status().isUnauthorized());
    }
}
