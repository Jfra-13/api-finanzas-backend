package com.finanzas.api.shared.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.Components;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// OpenAPI contract for the mobile (Kotlin) team. Declares a single bearer-JWT
// scheme so Swagger UI can call protected endpoints after pasting a token.
@Configuration
public class OpenApiConfig {

    private static final String BEARER_SCHEME = "bearerAuth";

    @Bean
    public OpenAPI finanzasOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Finanzas API")
                        .description("Backend del rastreador financiero para trabajadores independientes (MVP: taxistas).")
                        .version("v1")
                        .contact(new Contact().name("Equipo Finanzas")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME))
                .components(new Components()
                        .addSecuritySchemes(BEARER_SCHEME, new SecurityScheme()
                                .name(BEARER_SCHEME)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")));
    }
}
