package com.finanzas.api.usuario;

import com.finanzas.api.support.IntegrationTestSupport;
import com.finanzas.api.usuario.model.Usuario;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Guards the OTP lockout against brute force. Runs WITHOUT the base test
// transaction: the bug it covers (failed-attempt counter lost to a service
// rollback) only shows up when each request commits on its own, as in prod.
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class OtpBruteForceIntegrationTest extends IntegrationTestSupport {

    private static final String FORGOT = "/api/v1/usuarios/forgot-password";
    private static final String RESET = "/api/v1/usuarios/reset-password";
    private static final String VERIFY = "/api/v1/usuarios/verify-otp";
    private static final String OTP = "1234";

    @MockitoBean private EmailService emailService;

    private Usuario usuario;

    @AfterEach
    void limpiar() {
        if (usuario != null) {
            usuarioRepository.deleteById(usuario.getId());
        }
    }

    @Test
    void resetPassword_otpIncorrecto_cuentaIntentosYBloquea() throws Exception {
        usuario = conOtp(LocalDateTime.now().plusMinutes(10), 0);

        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post(RESET).contentType(APPLICATION_JSON).content(reset("9999")))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.code").value("OTP_INVALIDO"));
        }

        // Even the right code is refused once the attempts are exhausted.
        mockMvc.perform(post(RESET).contentType(APPLICATION_JSON).content(reset(OTP)))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("OTP_BLOQUEADO"));
        assertEquals(5, recargar().getIntentosOtp());
    }

    @Test
    void resetPassword_intentosEnParalelo_noSuperanElLimite() throws Exception {
        usuario = conOtp(LocalDateTime.now().plusMinutes(10), 0);
        int hilos = 30;
        CountDownLatch largada = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(hilos);
        List<Future<Integer>> respuestas = new ArrayList<>();
        for (int i = 0; i < hilos; i++) {
            respuestas.add(pool.submit(() -> {
                largada.await();
                return mockMvc.perform(post(RESET).contentType(APPLICATION_JSON).content(reset("9999")))
                        .andReturn().getResponse().getStatus();
            }));
        }
        largada.countDown();

        int comparados = 0;
        for (Future<Integer> r : respuestas) {
            if (r.get(30, TimeUnit.SECONDS) == 422) {
                comparados++;
            }
        }
        pool.shutdown();

        // Each guess must reserve an attempt before the code is compared, so a
        // parallel burst cannot get more than 5 guesses past the lock.
        assertTrue(comparados <= 5, "guesses compared: " + comparados);
        assertEquals(5, recargar().getIntentosOtp());
    }

    @Test
    void verifyOtp_correcto_noConsumeIntento() throws Exception {
        usuario = conOtp(LocalDateTime.now().plusMinutes(10), 4);

        mockMvc.perform(post(VERIFY).contentType(APPLICATION_JSON).content(forgot().replace("}", ",\"otp\":\"" + OTP + "\"}")))
                .andExpect(status().isOk());

        // The legit flow (verify, then reset) must not burn the last attempt.
        assertEquals(4, recargar().getIntentosOtp());
        mockMvc.perform(post(RESET).contentType(APPLICATION_JSON).content(reset(OTP)))
                .andExpect(status().isOk());
    }

    @Test
    void forgotPassword_conOtpVigente_noReiniciaIntentosNiReenvia() throws Exception {
        usuario = conOtp(LocalDateTime.now().plusMinutes(10), 5);

        mockMvc.perform(post(FORGOT).contentType(APPLICATION_JSON).content(forgot()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OTP_SENT"));

        Usuario recargado = recargar();
        assertEquals(5, recargado.getIntentosOtp());
        assertEquals(OTP, recargado.getCodigoOtp());
        verify(emailService, never()).enviarOtp(anyString(), anyString());
    }

    @Test
    void forgotPassword_conOtpVencido_emiteUnoNuevo() throws Exception {
        usuario = conOtp(LocalDateTime.now().minusMinutes(1), 5);

        mockMvc.perform(post(FORGOT).contentType(APPLICATION_JSON).content(forgot()))
                .andExpect(status().isOk());

        Usuario recargado = recargar();
        assertEquals(0, recargado.getIntentosOtp());
        assertTrue(recargado.getExpiracionOtp().isAfter(LocalDateTime.now()));
        verify(emailService).enviarOtp(eq(usuario.getEmail()), eq(recargado.getCodigoOtp()));
    }

    private Usuario conOtp(LocalDateTime expiracion, int intentos) {
        Usuario u = crearUsuario();
        u.setCodigoOtp(OTP);
        u.setExpiracionOtp(expiracion);
        u.setIntentosOtp(intentos);
        return usuarioRepository.save(u);
    }

    private Usuario recargar() {
        return usuarioRepository.findById(usuario.getId()).orElseThrow();
    }

    private String forgot() {
        return "{\"email\":\"" + usuario.getEmail() + "\"}";
    }

    private String reset(String otp) {
        return "{\"email\":\"" + usuario.getEmail() + "\",\"otp\":\"" + otp + "\",\"newPassword\":\"NuevaPass123\"}";
    }
}
