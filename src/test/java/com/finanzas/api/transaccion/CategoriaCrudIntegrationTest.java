package com.finanzas.api.transaccion;

import com.finanzas.api.support.IntegrationTestSupport;
import com.finanzas.api.transaccion.model.Categoria;
import com.finanzas.api.transaccion.model.TipoTransaccion;
import com.finanzas.api.transaccion.model.Transaccion;
import com.finanzas.api.usuario.model.Usuario;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CategoriaCrudIntegrationTest extends IntegrationTestSupport {

    private static final String BASE = "/api/v1/finanzas/categorias";

    @Test
    void actualizar_renombraCategoriaPropia() throws Exception {
        Usuario usuario = crearUsuario();
        Categoria categoria = crearCategoria("Gasolina", TipoTransaccion.EGRESO, usuario);

        mockMvc.perform(put(BASE + "/" + categoria.getId())
                        .header(AUTH, tokenDe(usuario))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\": \"Combustible\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("CATEGORY_UPDATED"))
                .andExpect(jsonPath("$.data.nombre").value("Combustible"))
                // The type never changes on rename.
                .andExpect(jsonPath("$.data.tipo").value("EGRESO"));
    }

    @Test
    void actualizar_categoriaBase_devuelve403() throws Exception {
        Usuario usuario = crearUsuario();
        Categoria base = crearCategoria("Peaje", TipoTransaccion.EGRESO, null); // system category

        mockMvc.perform(put(BASE + "/" + base.getId())
                        .header(AUTH, tokenDe(usuario))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\": \"Hackeada\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESO_DENEGADO"));
    }

    // Another user's category is a 404, not a 403: its existence must not leak.
    @Test
    void actualizar_categoriaAjena_devuelve404() throws Exception {
        Usuario duenio = crearUsuario();
        Usuario intruso = crearUsuario();
        Categoria ajena = crearCategoria("Privada", TipoTransaccion.EGRESO, duenio);

        mockMvc.perform(put(BASE + "/" + ajena.getId())
                        .header(AUTH, tokenDe(intruso))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\": \"Robada\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CATEGORIA_NO_ENCONTRADA"));
    }

    @Test
    void actualizar_nombreVacio_devuelveValidationError() throws Exception {
        Usuario usuario = crearUsuario();
        Categoria categoria = crearCategoria("Gasolina", TipoTransaccion.EGRESO, usuario);

        mockMvc.perform(put(BASE + "/" + categoria.getId())
                        .header(AUTH, tokenDe(usuario))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\": \"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void eliminar_conservaTransaccionesSinCategoria() throws Exception {
        Usuario usuario = crearUsuario();
        Categoria categoria = crearCategoria("Gasolina", TipoTransaccion.EGRESO, usuario);
        Transaccion transaccion = crearTransaccion(usuario, TipoTransaccion.EGRESO, "50.00",
                LocalDateTime.now(), categoria);

        mockMvc.perform(delete(BASE + "/" + categoria.getId()).header(AUTH, tokenDe(usuario)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("CATEGORY_DELETED"));

        assertTrue(categoriaRepository.findById(categoria.getId()).isEmpty());
        // The movement survives, now uncategorized (the bulk update clears the
        // persistence context, so this read comes fresh from the DB).
        Transaccion recargada = transaccionRepository.findById(transaccion.getId()).orElseThrow();
        assertNull(recargada.getCategoria());
    }

    @Test
    void eliminar_categoriaBase_devuelve403() throws Exception {
        Usuario usuario = crearUsuario();
        Categoria base = crearCategoria("Peaje", TipoTransaccion.EGRESO, null);

        mockMvc.perform(delete(BASE + "/" + base.getId()).header(AUTH, tokenDe(usuario)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESO_DENEGADO"));
    }

    @Test
    void eliminar_inexistente_devuelve404() throws Exception {
        Usuario usuario = crearUsuario();

        mockMvc.perform(delete(BASE + "/999999").header(AUTH, tokenDe(usuario)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CATEGORIA_NO_ENCONTRADA"));
    }
}
