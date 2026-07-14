package com.finanzas.api.usuario;

import com.finanzas.api.support.IntegrationTestSupport;
import com.finanzas.api.transaccion.model.Categoria;
import com.finanzas.api.transaccion.model.TipoTransaccion;
import com.finanzas.api.usuario.model.Usuario;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CuentaEliminacionIntegrationTest extends IntegrationTestSupport {

    private static final String USUARIOS = "/api/v1/usuarios";
    private static final String PASSWORD = "secreta123";

    @Autowired private CuentaPurgaJob cuentaPurgaJob;

    // Registers through the real endpoint so the stored hash matches PASSWORD.
    private String registrarYLoguear(String email) throws Exception {
        mockMvc.perform(post(USUARIOS + "/registro").contentType(APPLICATION_JSON)
                        .content("{\"nombre\":\"Test\",\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\",\"tipoNegocio\":\"TAXI\"}"))
                .andExpect(status().isOk());
        return mockMvc.perform(post(USUARIOS + "/login").contentType(APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    void eliminarCuenta_passwordIncorrecto_devuelve401() throws Exception {
        String login = registrarYLoguear("borrar1@test.com");
        String token = "Bearer " + JsonPath.read(login, "$.data.token");

        mockMvc.perform(post(USUARIOS + "/me/eliminar").header(AUTH, token).contentType(APPLICATION_JSON)
                        .content("{\"password\":\"equivocada\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("CREDENCIALES_INVALIDAS"));
    }

    @Test
    void eliminarCuenta_revocaSesionesYLoginDentroDeGraciaReactiva() throws Exception {
        String login = registrarYLoguear("borrar2@test.com");
        String token = "Bearer " + JsonPath.read(login, "$.data.token");
        String refreshToken = JsonPath.read(login, "$.data.refreshToken");

        mockMvc.perform(post(USUARIOS + "/me/eliminar").header(AUTH, token).contentType(APPLICATION_JSON)
                        .content("{\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("ACCOUNT_DELETED"));

        // Marked as deleted...
        Usuario usuario = usuarioRepository.findByEmail("borrar2@test.com").orElseThrow();
        assertTrue(usuario.getEliminadoEn() != null);

        // ...the still-valid access token is rejected right away...
        mockMvc.perform(get(USUARIOS + "/me").header(AUTH, token))
                .andExpect(status().isUnauthorized());

        // ...every refresh session is dead...
        mockMvc.perform(post(USUARIOS + "/refresh").contentType(APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + refreshToken + "\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("REFRESH_TOKEN_INVALIDO"));

        // ...and logging back in within the grace period reactivates the account,
        // flagging the reactivation so the client can inform the user.
        mockMvc.perform(post(USUARIOS + "/login").contentType(APPLICATION_JSON)
                        .content("{\"email\":\"borrar2@test.com\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("LOGIN_SUCCESS"))
                .andExpect(jsonPath("$.data.cuentaReactivada").value(true));
        assertNull(usuarioRepository.findByEmail("borrar2@test.com").orElseThrow().getEliminadoEn());

        // A normal login never carries the flag.
        mockMvc.perform(post(USUARIOS + "/login").contentType(APPLICATION_JSON)
                        .content("{\"email\":\"borrar2@test.com\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.cuentaReactivada").value(false));
    }

    @Test
    void login_pasadaLaGracia_devuelve404() throws Exception {
        registrarYLoguear("borrar3@test.com");
        Usuario usuario = usuarioRepository.findByEmail("borrar3@test.com").orElseThrow();
        usuario.setEliminadoEn(LocalDateTime.now().minusDays(31));
        usuarioRepository.save(usuario);

        mockMvc.perform(post(USUARIOS + "/login").contentType(APPLICATION_JSON)
                        .content("{\"email\":\"borrar3@test.com\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USUARIO_NO_ENCONTRADO"));
    }

    @Test
    void purga_eliminaDefinitivamenteCuentasVencidasConSusDatos() throws Exception {
        Usuario usuario = crearUsuario();
        Categoria propia = crearCategoria("Propia", TipoTransaccion.EGRESO, usuario);
        crearTransaccion(usuario, TipoTransaccion.EGRESO, "10.00", LocalDateTime.now(), propia);
        crearMeta(usuario, "1000.00", periodoActual(), "1,2,3,4,5");
        usuario.setEliminadoEn(LocalDateTime.now().minusDays(31));
        usuarioRepository.save(usuario);

        // A recently deleted account must survive the purge.
        Usuario enGracia = crearUsuario();
        enGracia.setEliminadoEn(LocalDateTime.now().minusDays(5));
        usuarioRepository.save(enGracia);

        cuentaPurgaJob.purgarCuentasVencidas();

        assertFalse(usuarioRepository.findById(usuario.getId()).isPresent());
        assertTrue(usuarioRepository.findById(enGracia.getId()).isPresent());
        assertFalse(categoriaRepository.findById(propia.getId()).isPresent());
    }
}
