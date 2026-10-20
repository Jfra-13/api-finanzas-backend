package com.finanzas.api.shared;

import com.finanzas.api.support.IntegrationTestSupport;
import org.junit.jupiter.api.Test;

import static org.springframework.http.MediaType.TEXT_PLAIN;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Client mistakes the framework detects before reaching a controller (unknown
// route, wrong method, wrong content type) are 4xx in the standard envelope,
// never the generic 500.
class ClientErrorIntegrationTest extends IntegrationTestSupport {

    @Test
    void rutaInexistente_devuelve404() throws Exception {
        mockMvc.perform(get("/api/v1/finanzas/no-existe").header(AUTH, tokenDe(crearUsuario())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void metodoNoSoportado_devuelve405() throws Exception {
        mockMvc.perform(delete("/api/v1/finanzas/hoy").header(AUTH, tokenDe(crearUsuario())))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
    }

    @Test
    void contentTypeNoSoportado_devuelve415() throws Exception {
        mockMvc.perform(post("/api/v1/finanzas/transacciones").header(AUTH, tokenDe(crearUsuario()))
                        .contentType(TEXT_PLAIN).content("hola"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"));
    }
}
