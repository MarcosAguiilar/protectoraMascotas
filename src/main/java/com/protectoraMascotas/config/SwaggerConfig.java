package com.protectoraMascotas.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;

@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI createApiDoc() {
        return new OpenAPI()
                .info(new Info()
                        .title("Documentación para registro de mascotas")
                        .description("API Rest para registro de mascotas para protectoras")
                        .termsOfService("www.protecotraMascotas.com")
                        .contact(new Contact().name("Marcos Aguilar")
                                .url("www.protecotraMascotas.com")
                                .email("marocsaguilar2001mal@gmail.com"))
                        .version("1.0"))
                .components(new Components().addSecuritySchemes("basicAuth",
                        new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("basic")));
    }
}
