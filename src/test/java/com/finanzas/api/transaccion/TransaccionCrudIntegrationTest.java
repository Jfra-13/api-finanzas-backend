package com.finanzas.api.transaccion;

import com.finanzas.api.support.IntegrationTestSupport;
import com.finanzas.api.transaccion.model.Categoria;
import com.finanzas.api.transaccion.model.TipoTransaccion;
import com.finanzas.api.transaccion.model.Transaccion;
import com.finanzas.api.usuario.model.Usuario;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TransaccionCrudIntegrationTest extends IntegrationTestSupport {

    private static final String TX = "/api/v1/finanzas/transacciones";

    @Test
    void listar_paginadoYOrdenadoFechaDesc() throws Exception {
        Usuario usuario = crearUsuario();
        LocalDateTime ahora = LocalDateTime.now();
        crearTransaccion(usuario, TipoTransaccion.INGRESO, "100.00", ahora.minusDays(2), null);
        crearTransaccion(usuario, TipoTransaccion.EGRESO, "50.00", ahora.minusDays(1), null);
        crearTransaccion(usuario, TipoTransaccion.INGRESO, "200.00", ahora, null);

        mockMvc.perform(get(TX).header(AUTH, tokenDe(usuario)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("TRANSACTIONS_OK"))
                .andExpect(jsonPath("$.data.content", hasSize(3)))
                .andExpect(jsonPath("$.data.totalElements").value(3))
                // Newest first by default.
                .andExpect(jsonPath("$.data.content[0].monto").value(200.00));
    }

    @Test
    void listar_filtraPorTipo() throws Exception {
        Usuario usuario = crearUsuario();
        LocalDateTime ahora = LocalDateTime.now();
        crearTransaccion(usuario, TipoTransaccion.INGRESO, "100.00", ahora, null);
        crearTransaccion(usuario, TipoTransaccion.EGRESO, "50.00", ahora, null);

        mockMvc.perform(get(TX).header(AUTH, tokenDe(usuario)).param("tipo", "EGRESO"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(1)))
                .andExpect(jsonPath("$.data.content[0].tipo").value("EGRESO"));
    }

    @Test
    void listar_filtraPorCategoria() throws Exception {
        Usuario usuario = crearUsuario();
        Categoria gasolina = crearCategoria("Gasolina", TipoTransaccion.EGRESO, null);
        LocalDateTime ahora = LocalDateTime.now();
        crearTransaccion(usuario, TipoTransaccion.EGRESO, "80.00", ahora, gasolina);
        crearTransaccion(usuario, TipoTransaccion.EGRESO, "30.00", ahora, null);

        mockMvc.perform(get(TX).header(AUTH, tokenDe(usuario)).param("categoriaId", gasolina.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(1)))
                .andExpect(jsonPath("$.data.content[0].monto").value(80.00));
    }

    @Test
    void actualizar_propia_ok() throws Exception {
        Usuario usuario = crearUsuario();
        Transaccion tx = crearTransaccion(usuario, TipoTransaccion.INGRESO, "100.00", LocalDateTime.now(), null);
        String body = "{\"id\":" + tx.getId() + ",\"monto\":999.00,\"tipo\":\"EGRESO\",\"descripcion\":\"corregida\"}";

        mockMvc.perform(put(TX + "/" + tx.getId()).header(AUTH, tokenDe(usuario)).contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("TRANSACTION_UPDATED"))
                .andExpect(jsonPath("$.data.monto").value(999.00))
                .andExpect(jsonPath("$.data.tipo").value("EGRESO"));
    }

    @Test
    void actualizar_deOtroUsuario_devuelve403() throws Exception {
        Usuario dueno = crearUsuario();
        Usuario intruso = crearUsuario();
        Transaccion tx = crearTransaccion(dueno, TipoTransaccion.INGRESO, "100.00", LocalDateTime.now(), null);
        String body = "{\"id\":" + tx.getId() + ",\"monto\":999.00,\"tipo\":\"EGRESO\"}";

        mockMvc.perform(put(TX + "/" + tx.getId()).header(AUTH, tokenDe(intruso)).contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESO_DENEGADO"));
    }

    @Test
    void actualizar_inexistente_devuelve404() throws Exception {
        Usuario usuario = crearUsuario();
        String body = "{\"id\":999999,\"monto\":10.00,\"tipo\":\"INGRESO\"}";

        mockMvc.perform(put(TX + "/999999").header(AUTH, tokenDe(usuario)).contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TRANSACCION_NO_ENCONTRADA"));
    }

    @Test
    void eliminar_propia_ok() throws Exception {
        Usuario usuario = crearUsuario();
        String token = tokenDe(usuario);
        Transaccion tx = crearTransaccion(usuario, TipoTransaccion.INGRESO, "100.00", LocalDateTime.now(), null);

        mockMvc.perform(delete(TX + "/" + tx.getId()).header(AUTH, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("TRANSACTION_DELETED"));

        // Gone from the history afterwards.
        mockMvc.perform(get(TX).header(AUTH, token))
                .andExpect(jsonPath("$.data.content", hasSize(0)));
    }

    @Test
    void eliminar_deOtroUsuario_devuelve403() throws Exception {
        Usuario dueno = crearUsuario();
        Usuario intruso = crearUsuario();
        Transaccion tx = crearTransaccion(dueno, TipoTransaccion.INGRESO, "100.00", LocalDateTime.now(), null);

        mockMvc.perform(delete(TX + "/" + tx.getId()).header(AUTH, tokenDe(intruso)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESO_DENEGADO"));
    }

    @Test
    void eliminar_inexistente_devuelve404() throws Exception {
        Usuario usuario = crearUsuario();
        mockMvc.perform(delete(TX + "/999999").header(AUTH, tokenDe(usuario)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TRANSACCION_NO_ENCONTRADA"));
    }

    @Test
    void listar_sinToken_devuelve401() throws Exception {
        mockMvc.perform(get(TX)).andExpect(status().isUnauthorized());
    }
}
