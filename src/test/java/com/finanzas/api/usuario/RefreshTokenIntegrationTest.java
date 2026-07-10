package com.finanzas.api.usuario;

import com.finanzas.api.support.IntegrationTestSupport;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class RefreshTokenIntegrationTest extends IntegrationTestSupport {

    private static final String REGISTRO = "/api/v1/usuarios/registro";
    private static final String LOGIN = "/api/v1/usuarios/login";
    private static final String REFRESH = "/api/v1/usuarios/refresh";
    private static final String LOGOUT = "/api/v1/usuarios/logout";

    @Test
    void login_devuelveAccessYRefreshToken() throws Exception {
        registrar("rt1@test.com");

        mockMvc.perform(post(LOGIN).contentType(APPLICATION_JSON).content(login("rt1@test.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.token").isNotEmpty())
                .andExpect(jsonPath("$.data.refreshToken").isNotEmpty());
    }

    @Test
    void refresh_ok_rotaTokens() throws Exception {
        registrar("rt2@test.com");
        String refreshOriginal = refreshTokenDeLogin("rt2@test.com");

        String body = "{\"refreshToken\":\"" + refreshOriginal + "\"}";
        String respuesta = mockMvc.perform(post(REFRESH).contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("TOKEN_REFRESHED"))
                .andExpect(jsonPath("$.data.token").isNotEmpty())
                .andExpect(jsonPath("$.data.refreshToken").isNotEmpty())
                .andReturn().getResponse().getContentAsString();

        String refreshNuevo = JsonPath.read(respuesta, "$.data.refreshToken");
        assertNotEquals(refreshOriginal, refreshNuevo);
    }

    @Test
    void refresh_tokenViejoTrasRotacion_devuelve401() throws Exception {
        registrar("rt3@test.com");
        String refreshOriginal = refreshTokenDeLogin("rt3@test.com");

        String body = "{\"refreshToken\":\"" + refreshOriginal + "\"}";
        // First use rotates and revokes it.
        mockMvc.perform(post(REFRESH).contentType(APPLICATION_JSON).content(body)).andExpect(status().isOk());

        // Reusing the now-revoked token must fail.
        mockMvc.perform(post(REFRESH).contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("REFRESH_TOKEN_INVALIDO"));
    }

    @Test
    void refresh_tokenInvalido_devuelve401() throws Exception {
        mockMvc.perform(post(REFRESH).contentType(APPLICATION_JSON).content("{\"refreshToken\":\"no-existe\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("REFRESH_TOKEN_INVALIDO"));
    }

    // Logout revokes the refresh token server-side: it can no longer be exchanged.
    @Test
    void logout_revocaRefreshToken_yRefreshPosteriorFalla() throws Exception {
        registrar("rt4@test.com");
        String refresh = refreshTokenDeLogin("rt4@test.com");
        String body = "{\"refreshToken\":\"" + refresh + "\"}";

        mockMvc.perform(post(LOGOUT).contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("LOGGED_OUT"));

        mockMvc.perform(post(REFRESH).contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("REFRESH_TOKEN_INVALIDO"));
    }

    // Idempotent: repeating logout (or sending an unknown token) is still a 200.
    @Test
    void logout_esIdempotente() throws Exception {
        registrar("rt5@test.com");
        String refresh = refreshTokenDeLogin("rt5@test.com");
        String body = "{\"refreshToken\":\"" + refresh + "\"}";

        mockMvc.perform(post(LOGOUT).contentType(APPLICATION_JSON).content(body)).andExpect(status().isOk());
        mockMvc.perform(post(LOGOUT).contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("LOGGED_OUT"));

        mockMvc.perform(post(LOGOUT).contentType(APPLICATION_JSON).content("{\"refreshToken\":\"desconocido\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("LOGGED_OUT"));
    }

    private void registrar(String email) throws Exception {
        String body = "{\"nombre\":\"RT\",\"email\":\"" + email + "\",\"password\":\"Password123\",\"tipoNegocio\":\"TAXI\"}";
        mockMvc.perform(post(REGISTRO).contentType(APPLICATION_JSON).content(body)).andExpect(status().isOk());
    }

    private String login(String email) {
        return "{\"email\":\"" + email + "\",\"password\":\"Password123\"}";
    }

    private String refreshTokenDeLogin(String email) throws Exception {
        String respuesta = mockMvc.perform(post(LOGIN).contentType(APPLICATION_JSON).content(login(email)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(respuesta, "$.data.refreshToken");
    }
}
