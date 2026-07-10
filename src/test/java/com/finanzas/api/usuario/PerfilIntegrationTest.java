package com.finanzas.api.usuario;

import com.finanzas.api.support.IntegrationTestSupport;
import com.finanzas.api.usuario.model.Usuario;
import org.junit.jupiter.api.Test;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PerfilIntegrationTest extends IntegrationTestSupport {

    private static final String ME = "/api/v1/usuarios/me";

    @Test
    void obtenerPerfil_devuelveDatosDelUsuario() throws Exception {
        Usuario usuario = crearUsuario("perfil@test.com");

        mockMvc.perform(get(ME).header(AUTH, tokenDe(usuario)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("PROFILE_OK"))
                .andExpect(jsonPath("$.data.id").value(usuario.getId()))
                .andExpect(jsonPath("$.data.nombre").value("Test User"))
                .andExpect(jsonPath("$.data.email").value("perfil@test.com"))
                .andExpect(jsonPath("$.data.tipoNegocio").value("TAXI"))
                // Nullable contract fields must be present (as null), not absent.
                .andExpect(jsonPath("$.data", org.hamcrest.Matchers.hasKey("telefono")))
                .andExpect(jsonPath("$.data", org.hamcrest.Matchers.hasKey("fotoUrl")))
                .andExpect(jsonPath("$.data", org.hamcrest.Matchers.hasKey("plan")));
    }

    @Test
    void actualizarPerfil_cambiaSoloCamposPresentes() throws Exception {
        Usuario usuario = crearUsuario();

        mockMvc.perform(put(ME).header(AUTH, tokenDe(usuario))
                        .contentType(APPLICATION_JSON)
                        .content("{\"telefono\": \"+51 999 888 777\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("PROFILE_UPDATED"))
                .andExpect(jsonPath("$.data.telefono").value("+51 999 888 777"))
                // Name untouched by the partial update.
                .andExpect(jsonPath("$.data.nombre").value("Test User"));
    }

    @Test
    void actualizarPerfil_telefonoLargo_devuelveValidationError() throws Exception {
        Usuario usuario = crearUsuario();

        mockMvc.perform(put(ME).header(AUTH, tokenDe(usuario))
                        .contentType(APPLICATION_JSON)
                        .content("{\"telefono\": \"123456789012345678901\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void obtenerPerfil_sinToken_devuelve401() throws Exception {
        mockMvc.perform(get(ME)).andExpect(status().isUnauthorized());
    }
}
