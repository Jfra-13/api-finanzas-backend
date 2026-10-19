package com.finanzas.api.shared;

import com.finanzas.api.support.IntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

// Mirrors the prod profile switches (application-prod.properties.example): with
// them, neither the OpenAPI JSON nor Swagger UI is served.
@TestPropertySource(properties = {
        "springdoc.api-docs.enabled=false",
        "springdoc.swagger-ui.enabled=false"
})
class OpenApiDisabledIntegrationTest extends IntegrationTestSupport {

    @Test
    void apiDocsYSwaggerUi_noSeSirven() throws Exception {
        for (String ruta : new String[]{"/v3/api-docs", "/swagger-ui.html", "/swagger-ui/index.html"}) {
            int status = mockMvc.perform(get(ruta)).andReturn().getResponse().getStatus();
            assertNotEquals(200, status, ruta);
        }
    }
}
