package com.protectoraMascotas.config;

import jakarta.servlet.DispatcherType;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.boot.security.autoconfigure.web.servlet.PathRequest;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final Environment environment;

    public SecurityConfig(Environment environment) {
        this.environment = environment;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        boolean localConsole = environment.acceptsProfiles(Profiles.of("dev & !prod"))
                && environment.getProperty("spring.h2.console.enabled", Boolean.class, false);

        if (localConsole) {
            http.authorizeHttpRequests(auth -> auth.requestMatchers(PathRequest.toH2Console()).authenticated());
            // La consola local no integra tokens CSRF de Spring; la API conserva su proteccion.
            http.csrf(csrf -> csrf.ignoringRequestMatchers(PathRequest.toH2Console()));
            http.headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()));
        }

        http.authorizeHttpRequests(auth -> auth
                .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                .requestMatchers(HttpMethod.GET, "/api/mascotas", "/api/mascotas/**").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/mascotas").authenticated()
                .requestMatchers(HttpMethod.DELETE, "/api/mascotas/delete/*").authenticated()
                .requestMatchers(HttpMethod.GET, "/api/protectoras", "/api/protectoras/",
                        "/api/protectoras/{id}", "/api/protectoras/{id}/").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/protectoras").authenticated()
                .requestMatchers(HttpMethod.GET, "/api/protectoras/{id}/mascotas",
                        "/api/protectoras/{id}/mascotas/").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/solicitudes-adopcion").authenticated()
                .requestMatchers(HttpMethod.PATCH, "/api/solicitudes-adopcion/{id}/estado").authenticated()
                .requestMatchers(HttpMethod.GET, "/api/solicitudes-adopcion", "/api/solicitudes-adopcion/",
                        "/api/solicitudes-adopcion/{id}", "/api/solicitudes-adopcion/{id}/").authenticated()
                .requestMatchers(HttpMethod.GET, "/api/csrf", "/swagger-ui.html", "/swagger-ui/**",
                        "/v3/api-docs", "/v3/api-docs/**", "/v3/api-docs.yaml").authenticated()
                .anyRequest().denyAll())
                .httpBasic(Customizer.withDefaults())
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable);
        return http.build();
    }
}
