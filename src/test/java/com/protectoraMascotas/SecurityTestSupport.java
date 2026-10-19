package com.protectoraMascotas;

import java.util.UUID;

import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("prod")
abstract class SecurityTestSupport {

    protected static final String USERNAME = "test-" + UUID.randomUUID();
    protected static final String PASSWORD = UUID.randomUUID().toString();

    @DynamicPropertySource
    static void credentials(DynamicPropertyRegistry registry) {
        registry.add("app.security.username", () -> USERNAME);
        registry.add("app.security.password", () -> PASSWORD);
    }
}
