package com.finanzas.api.transaccion;

import com.finanzas.api.support.IntegrationTestSupport;
import com.finanzas.api.transaccion.model.Categoria;
import com.finanzas.api.transaccion.model.Presupuesto;
import com.finanzas.api.transaccion.model.TipoTransaccion;
import com.finanzas.api.usuario.model.Usuario;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PresupuestoIntegrationTest extends IntegrationTestSupport {

    private static final String PRES = "/api/v1/finanzas/presupuestos";

    @org.springframework.beans.factory.annotation.Autowired
    private PresupuestoRepository presupuestoRepository;

    @Test
    void guardar_calculaEstadoDelMesYExcedido() throws Exception {
        Usuario usuario = crearUsuario();
        Categoria gasolina = crearCategoria("Gasolina", TipoTransaccion.EGRESO, null);
        crearTransaccion(usuario, TipoTransaccion.EGRESO, "320.00", LocalDateTime.now(), gasolina);
        String body = "{\"categoriaId\":" + gasolina.getId() + ",\"montoMensual\":300.00}";

        mockMvc.perform(post(PRES).header(AUTH, tokenDe(usuario)).contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("BUDGET_SET"))
                .andExpect(jsonPath("$.data.categoriaNombre").value("Gasolina"))
                .andExpect(jsonPath("$.data.montoMensual").value(300.00))
                .andExpect(jsonPath("$.data.gastadoMes").value(320.00))
                .andExpect(jsonPath("$.data.restante").value(-20.00))
                // 320 / 300 * 100 = 106.666... → 106.7 HALF_UP.
                .andExpect(jsonPath("$.data.consumoPct").value(106.7))
                .andExpect(jsonPath("$.data.excedido").value(true));
    }

    @Test
    void guardar_reenviarReemplazaElMonto() throws Exception {
        Usuario usuario = crearUsuario();
        Categoria gasolina = crearCategoria("Gasolina", TipoTransaccion.EGRESO, null);
        String token = tokenDe(usuario);

        mockMvc.perform(post(PRES).header(AUTH, token).contentType(APPLICATION_JSON)
                        .content("{\"categoriaId\":" + gasolina.getId() + ",\"montoMensual\":300.00}"))
                .andExpect(status().isOk());
        mockMvc.perform(post(PRES).header(AUTH, token).contentType(APPLICATION_JSON)
                        .content("{\"categoriaId\":" + gasolina.getId() + ",\"montoMensual\":500.00}"))
                .andExpect(status().isOk());

        // One budget per (usuario, categoria): the second post replaced, not duplicated.
        mockMvc.perform(get(PRES).header(AUTH, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("BUDGETS_OK"))
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].montoMensual").value(500.00));
    }

    @Test
    void guardar_categoriaInexistente_devuelve404() throws Exception {
        Usuario usuario = crearUsuario();
        mockMvc.perform(post(PRES).header(AUTH, tokenDe(usuario)).contentType(APPLICATION_JSON)
                        .content("{\"categoriaId\":999999,\"montoMensual\":100.00}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CATEGORIA_NO_ENCONTRADA"));
    }

    @Test
    void guardar_montoInvalido_devuelve400() throws Exception {
        Usuario usuario = crearUsuario();
        Categoria gasolina = crearCategoria("Gasolina", TipoTransaccion.EGRESO, null);
        mockMvc.perform(post(PRES).header(AUTH, tokenDe(usuario)).contentType(APPLICATION_JSON)
                        .content("{\"categoriaId\":" + gasolina.getId() + ",\"montoMensual\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void eliminar_propio_ok() throws Exception {
        Usuario usuario = crearUsuario();
        Categoria gasolina = crearCategoria("Gasolina", TipoTransaccion.EGRESO, null);
        String token = tokenDe(usuario);
        mockMvc.perform(post(PRES).header(AUTH, token).contentType(APPLICATION_JSON)
                .content("{\"categoriaId\":" + gasolina.getId() + ",\"montoMensual\":300.00}"));
        Long id = presupuestoRepository.findByUsuarioId(usuario.getId()).get(0).getId();

        mockMvc.perform(delete(PRES + "/" + id).header(AUTH, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("BUDGET_DELETED"));
        mockMvc.perform(get(PRES).header(AUTH, token))
                .andExpect(jsonPath("$.data", hasSize(0)));
    }

    @Test
    void eliminar_inexistente_devuelve404() throws Exception {
        Usuario usuario = crearUsuario();
        mockMvc.perform(delete(PRES + "/999999").header(AUTH, tokenDe(usuario)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PRESUPUESTO_NO_ENCONTRADO"));
    }

    @Test
    void eliminar_deOtroUsuario_devuelve403() throws Exception {
        Usuario dueno = crearUsuario();
        Usuario intruso = crearUsuario();
        Categoria gasolina = crearCategoria("Gasolina", TipoTransaccion.EGRESO, null);
        mockMvc.perform(post(PRES).header(AUTH, tokenDe(dueno)).contentType(APPLICATION_JSON)
                .content("{\"categoriaId\":" + gasolina.getId() + ",\"montoMensual\":300.00}"));
        Presupuesto propio = presupuestoRepository.findByUsuarioId(dueno.getId()).get(0);

        mockMvc.perform(delete(PRES + "/" + propio.getId()).header(AUTH, tokenDe(intruso)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESO_DENEGADO"));
    }

    @Test
    void listar_sinToken_devuelve401() throws Exception {
        mockMvc.perform(get(PRES)).andExpect(status().isUnauthorized());
    }
}
