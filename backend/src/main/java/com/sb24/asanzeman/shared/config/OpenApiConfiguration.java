package com.sb24.asanzeman.shared.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class OpenApiConfiguration {

    public static final String BEARER = "bearerJwt";

    @Bean
    OpenAPI asanZemanOpenApi() {
        return new OpenAPI()
                .info(new Info().title("Asan Zeman API").version("v1")
                        .description("Travel-return guarantee issuance. Errors are RFC 7807 Problem Details."))
                .components(new Components().addSecuritySchemes(BEARER,
                        new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")));
    }
}
