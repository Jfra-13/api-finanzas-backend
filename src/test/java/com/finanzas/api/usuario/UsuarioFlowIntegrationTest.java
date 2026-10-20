package com.finanzas.api.usuario;

import com.finanzas.api.support.IntegrationTestSupport;
import com.finanzas.api.usuario.model.Usuario;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

// Covers the user account flow end to end through the real security/JPA stack:
// registration (incl. tipoNegocio persistence) and login (success + bad creds).
class UsuarioFlowIntegrationTest extends IntegrationTestSupport {

    private static final String REGISTRO = "/api/v1/usuarios/registro";
    private static final String LOGIN = "/api/v1/usuarios/login";

    @MockitoBean private EmailService emailService;

    @Test
    void registro_ok_persisteUsuarioConTipoNegocio() throws Exception {
        String body = "{\"nombre\":\"Juana\",\"email\":\"juana@test.com\",\"password\":\"Password123\",\"tipoNegocio\":\"TAXI\"}";

        mockMvc.perform(post(REGISTRO).contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("USER_REGISTERED"));

        Usuario guardado = usuarioRepository.findByEmail("juana@test.com").orElseThrow();
        assertEquals("TAXI", guardado.getTipoNegocio());
        assertNotNull(guardado.getPasswordHash());
    }

    @Test
    void registro_emailExistente_respondeIgualSinTocarLaCuentaYAvisaAlDueno() throws Exception {
        String original = "{\"nombre\":\"Juana\",\"email\":\"dup@test.com\",\"password\":\"Password123\",\"tipoNegocio\":\"TAXI\"}";
        mockMvc.perform(post(REGISTRO).contentType(APPLICATION_JSON).content(original)).andExpect(status().isOk());
        String hashOriginal = usuarioRepository.findByEmail("dup@test.com").orElseThrow().getPasswordHash();
        verify(emailService, never()).avisarRegistroConEmailExistente(anyString());

        // Same answer as a fresh sign-up, so the endpoint cannot be used to find accounts.
        String intruso = "{\"nombre\":\"Otro\",\"email\":\"dup@test.com\",\"password\":\"OtraPass123\",\"tipoNegocio\":\"BODEGA\"}";
        mockMvc.perform(post(REGISTRO).contentType(APPLICATION_JSON).content(intruso))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("USER_REGISTERED"));

        Usuario guardado = usuarioRepository.findByEmail("dup@test.com").orElseThrow();
        assertEquals(hashOriginal, guardado.getPasswordHash());
        assertEquals("Juana", guardado.getNombre());
        assertEquals("TAXI", guardado.getTipoNegocio());
        verify(emailService).avisarRegistroConEmailExistente("dup@test.com");
    }

    @Test
    void registro_passwordInvalida_devuelve400() throws Exception {
        // Too short and no digit -> bean validation rejects it.
        String body = "{\"nombre\":\"Juana\",\"email\":\"weak@test.com\",\"password\":\"abc\",\"tipoNegocio\":\"TAXI\"}";
        mockMvc.perform(post(REGISTRO).contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void login_ok_devuelveToken() throws Exception {
        String registro = "{\"nombre\":\"Pedro\",\"email\":\"pedro@test.com\",\"password\":\"Password123\",\"tipoNegocio\":\"TAXI\"}";
        mockMvc.perform(post(REGISTRO).contentType(APPLICATION_JSON).content(registro)).andExpect(status().isOk());

        String login = "{\"email\":\"pedro@test.com\",\"password\":\"Password123\"}";
        mockMvc.perform(post(LOGIN).contentType(APPLICATION_JSON).content(login))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("LOGIN_SUCCESS"))
                .andExpect(jsonPath("$.data.token").isNotEmpty())
                .andExpect(jsonPath("$.data.email").value("pedro@test.com"))
                .andExpect(jsonPath("$.data.tipoNegocio").value("TAXI"));
    }

    @Test
    void login_credencialesInvalidas_devuelve401() throws Exception {
        String registro = "{\"nombre\":\"Ana\",\"email\":\"ana@test.com\",\"password\":\"Password123\",\"tipoNegocio\":\"TAXI\"}";
        mockMvc.perform(post(REGISTRO).contentType(APPLICATION_JSON).content(registro)).andExpect(status().isOk());

        String login = "{\"email\":\"ana@test.com\",\"password\":\"WrongPass123\"}";
        mockMvc.perform(post(LOGIN).contentType(APPLICATION_JSON).content(login))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").value("Credenciales inválidas"));
    }
}
