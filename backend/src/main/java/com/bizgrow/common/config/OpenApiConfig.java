package com.bizgrow.common.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Static OpenAPI description served at /swagger-ui.html (dev) — the living API
 * contract. The bearer scheme documents that protected endpoints require
 * {@code Authorization: Bearer <access token>}.
 */
@Configuration
public class OpenApiConfig {

    public static final String BEARER_SCHEME = "bearerAuth";

    @Bean
    public OpenAPI bizgrowOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("YemomTech (BizGrow) API")
                        .version("v1")
                        .description("AI-powered digital growth and business management SaaS — REST API. "
                                + "All tenant-scoped endpoints require membership of the organization in the path."))
                .components(new Components().addSecuritySchemes(BEARER_SCHEME,
                        new SecurityScheme()
                                .name(BEARER_SCHEME)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME));
    }
}
