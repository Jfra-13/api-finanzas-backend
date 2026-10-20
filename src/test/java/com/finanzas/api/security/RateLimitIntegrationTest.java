package com.finanzas.api.security;

import com.finanzas.api.support.IntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Per-IP rate limit on the public auth endpoints: one shared bucket per IP,
// so spreading requests across endpoints does not dodge the limit.
// Each test uses its own IP because the limiter lives in a shared singleton.
@TestPropertySource(properties = "rate-limit.max-por-minuto=3")
class RateLimitIntegrationTest extends IntegrationTestSupport {

    private static final String LOGIN_BODY = "{\"email\":\"rl1@test.com\",\"password\":\"WrongPass123\"}";
    private static final String OTRO_LOGIN_BODY = "{\"email\":\"rl2@test.com\",\"password\":\"WrongPass123\"}";

    @Test
    void endpointsPublicos_superanElLimitePorIp_responde429() throws Exception {
        String ip = "10.0.0.1";
        mockMvc.perform(desde(ip, post("/api/v1/usuarios/login").content(LOGIN_BODY)))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(desde(ip, post("/api/v1/usuarios/forgot-password").content("{\"email\":\"rl@test.com\"}")))
                .andExpect(status().isOk());
        mockMvc.perform(desde(ip, post("/api/v1/usuarios/login").content(LOGIN_BODY)))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(desde(ip, post("/api/v1/usuarios/registro").content(
                        "{\"nombre\":\"Rl\",\"email\":\"rl@test.com\",\"password\":\"Password123\",\"tipoNegocio\":\"TAXI\"}")))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("RATE_LIMIT_EXCEDIDO"));
    }

    @Test
    void otraIp_noComparteElLimite() throws Exception {
        for (int i = 0; i < 4; i++) {
            mockMvc.perform(desde("10.0.0.2", post("/api/v1/usuarios/login").content(OTRO_LOGIN_BODY)));
        }

        mockMvc.perform(desde("10.0.0.3", post("/api/v1/usuarios/login").content(OTRO_LOGIN_BODY)))
                .andExpect(status().isUnauthorized());
    }

    private MockHttpServletRequestBuilder desde(String ip, MockHttpServletRequestBuilder request) {
        return request.contentType(APPLICATION_JSON).with(r -> {
            r.setRemoteAddr(ip);
            return r;
        });
    }
}
