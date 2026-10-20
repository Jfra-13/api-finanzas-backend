package com.finanzas.api.usuario;

import com.finanzas.api.support.IntegrationTestSupport;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Login lockout: 5 failures lock the email for 15 minutes, a success resets the
// counter, and unknown emails lock the same way (no account enumeration).
// Each test uses its own email because the guard lives in a shared singleton.
class LoginBruteForceIntegrationTest extends IntegrationTestSupport {

    private static final String REGISTRO = "/api/v1/usuarios/registro";
    private static final String LOGIN = "/api/v1/usuarios/login";
    private static final String PASSWORD = "Password123";

    @Test
    void login_cincoFallos_bloqueaInclusoConPasswordCorrecta() throws Exception {
        String email = registrar("lock1@test.com");
        fallar(email, 5);

        mockMvc.perform(post(LOGIN).contentType(APPLICATION_JSON).content(login(email, PASSWORD)))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("LOGIN_BLOQUEADO"));
    }

    @Test
    void login_exitoso_reiniciaElContador() throws Exception {
        String email = registrar("lock2@test.com");
        fallar(email, 4);

        mockMvc.perform(post(LOGIN).contentType(APPLICATION_JSON).content(login(email, PASSWORD)))
                .andExpect(status().isOk());
        fallar(email, 4);

        mockMvc.perform(post(LOGIN).contentType(APPLICATION_JSON).content(login(email, PASSWORD)))
                .andExpect(status().isOk());
    }

    @Test
    void login_emailInexistente_seBloqueaIgual() throws Exception {
        String email = "ghost-lock@test.com";
        fallar(email, 5);

        mockMvc.perform(post(LOGIN).contentType(APPLICATION_JSON).content(login(email, PASSWORD)))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("LOGIN_BLOQUEADO"));
    }

    @Test
    void login_intentosEnParalelo_noSuperanElLimite() throws Exception {
        String email = "burst-lock@test.com";
        int hilos = 30;
        CountDownLatch largada = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(hilos);
        List<Future<Integer>> respuestas = new ArrayList<>();
        for (int i = 0; i < hilos; i++) {
            respuestas.add(pool.submit(() -> {
                largada.await();
                return mockMvc.perform(post(LOGIN).contentType(APPLICATION_JSON).content(login(email, "WrongPass123")))
                        .andReturn().getResponse().getStatus();
            }));
        }
        largada.countDown();

        int autenticados = 0;
        for (Future<Integer> r : respuestas) {
            if (r.get(30, TimeUnit.SECONDS) == 401) {
                autenticados++;
            }
        }
        pool.shutdown();

        // The attempt is reserved before the (slow) password check, so a parallel
        // burst cannot get more than 5 password checks past the lock.
        assertTrue(autenticados <= 5, "password checks: " + autenticados);
    }

    private String registrar(String email) throws Exception {
        String body = "{\"nombre\":\"Lock\",\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\",\"tipoNegocio\":\"TAXI\"}";
        mockMvc.perform(post(REGISTRO).contentType(APPLICATION_JSON).content(body)).andExpect(status().isOk());
        return email;
    }

    private void fallar(String email, int veces) throws Exception {
        for (int i = 0; i < veces; i++) {
            mockMvc.perform(post(LOGIN).contentType(APPLICATION_JSON).content(login(email, "WrongPass123")))
                    .andExpect(status().isUnauthorized());
        }
    }

    private String login(String email, String password) {
        return "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}";
    }
}
