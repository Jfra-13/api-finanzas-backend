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
    void fijarMeta_sinToken_devuelve401() throws Exception {
        mockMvc.perform(post(METAS).contentType(APPLICATION_JSON)
                        .content("{\"montoObjetivo\":3000.00,\"diasLaborables\":[1,2,3]}"))
                .andExpect(status().isUnauthorized());
    }
}
