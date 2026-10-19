package com.protectoraMascotas;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.security.core.userdetails.UserDetailsService;

import com.protectoraMascotas.config.ApiCredentialsConfig;

class SecurityConfigurationTests {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withUserConfiguration(ApiCredentialsConfig.class);

    @ParameterizedTest
    @CsvSource({"default,username", "default,password", "dev,username", "dev,password",
            "prod,username", "prod,password"})
    void faltaDeCredencialesImpideArranque(String profile, String missing) {
        runner.withPropertyValues("spring.profiles.active=" + profile,
                        "app.security.username=" + (missing.equals("username") ? "" : UUID.randomUUID()),
                        "app.security.password=" + (missing.equals("password") ? "" : UUID.randomUUID()))
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseMessage("Configure API_USERNAME y API_PASSWORD para iniciar la API");
                });
    }

    @Test
    void espaciosNoSonCredencialesValidas() {
        runner.withPropertyValues("spring.profiles.active=prod", "app.security.username=   ",
                        "app.security.password=   ")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void desarrolloUsaPostgresqlLocalSinConsolaH2() {
        configuredRunner().withPropertyValues("spring.profiles.active=dev").run(context -> {
            assertThat(context).hasNotFailed().hasSingleBean(UserDetailsService.class);
            assertThat(context.getEnvironment().getProperty("server.address")).isEqualTo("127.0.0.1");
            assertThat(context.getEnvironment().getProperty("spring.h2.console.enabled")).isEqualTo("false");
            assertThat(context.getEnvironment().getProperty("spring.datasource.url"))
                    .startsWith("jdbc:postgresql://");
        });
    }

    @Test
    void perfilPorDefectoDeshabilitaConsola() {
        configuredRunner().withPropertyValues("spring.profiles.active=default").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getEnvironment().getProperty("spring.h2.console.enabled")).isEqualTo("false");
        });
    }

    @Test
    void produccionNoPermiteReactivarConsola() {
        configuredRunner().withPropertyValues("spring.profiles.active=prod", "spring.h2.console.enabled=true")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).hasRootCauseMessage(
                            "La consola H2 solo puede habilitarse en el perfil dev sin prod");
                });
    }

    @Test
    void activarDevJuntoAProdNoPermiteConsola() {
        configuredRunner().withPropertyValues("spring.profiles.active=prod,dev", "spring.h2.console.enabled=true")
                .run(context -> assertThat(context).hasFailed());
    }

    private ApplicationContextRunner configuredRunner() {
        return runner.withPropertyValues("app.security.username=" + UUID.randomUUID(),
                "app.security.password=" + UUID.randomUUID());
    }
}
