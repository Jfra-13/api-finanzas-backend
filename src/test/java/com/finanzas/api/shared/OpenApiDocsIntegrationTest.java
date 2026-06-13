package com.finanzas.api.shared;

import com.finanzas.api.support.IntegrationTestSupport;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Locks the API contract: the OpenAPI doc is reachable without auth and exposes
// the documented info and endpoints the Kotlin team consumes.
class OpenApiDocsIntegrationTest extends IntegrationTestSupport {

    @Test
    void apiDocs_accesibleSinToken() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("Finanzas API"));
    }

    @Test
    void apiDocs_exponeEndpointsClave() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/finanzas/transacciones']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/usuarios/login']").exists())
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"));
    }
}
