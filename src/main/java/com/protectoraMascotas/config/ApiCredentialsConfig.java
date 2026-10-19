package com.protectoraMascotas.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.util.StringUtils;

@Configuration
public class ApiCredentialsConfig {

    @Bean
    public UserDetailsService apiUsers(Environment environment) {
        String username = environment.getProperty("app.security.username", "");
        String password = environment.getProperty("app.security.password", "");
        if (!StringUtils.hasText(username) || !StringUtils.hasText(password)) {
            throw new IllegalStateException("Configure API_USERNAME y API_PASSWORD para iniciar la API");
        }
        boolean local = environment.acceptsProfiles(Profiles.of("dev & !prod"));
        if (environment.getProperty("spring.h2.console.enabled", Boolean.class, false) && !local) {
            throw new IllegalStateException("La consola H2 solo puede habilitarse en el perfil dev sin prod");
        }
        return new InMemoryUserDetailsManager(User.withUsername(username)
                .password("{bcrypt}" + new BCryptPasswordEncoder().encode(password))
                .authorities("API_USER")
                .build());
    }
}
