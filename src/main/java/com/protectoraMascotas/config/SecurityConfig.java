package com.protectoraMascotas.config;

import javax.servlet.DispatcherType;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.boot.autoconfigure.security.servlet.PathRequest;

@Configuration
@EnableWebSecurity
public class SecurityConfig extends WebSecurityConfigurerAdapter {

    private final Environment environment;

    public SecurityConfig(Environment environment) {
        this.environment = environment;
    }

    @Override
    protected void configure(HttpSecurity http) throws Exception {
        boolean localConsole = environment.acceptsProfiles(Profiles.of("dev & !prod"))
                && environment.getProperty("spring.h2.console.enabled", Boolean.class, false);

        if (localConsole) {
            http.authorizeRequests().requestMatchers(PathRequest.toH2Console()).authenticated();
            // La consola local no integra tokens CSRF de Spring; la API conserva su proteccion.
            http.csrf().ignoringRequestMatchers(PathRequest.toH2Console());
            http.headers().frameOptions().sameOrigin();
        }

        http.authorizeRequests()
                .requestMatchers(request -> request.getDispatcherType() == DispatcherType.ERROR).permitAll()
                .antMatchers(HttpMethod.GET, "/api/mascotas", "/api/mascotas/**").permitAll()
                .antMatchers(HttpMethod.POST, "/api/mascotas").authenticated()
                .antMatchers(HttpMethod.DELETE, "/api/mascotas/delete/*").authenticated()
                .antMatchers(HttpMethod.GET, "/api/csrf", "/swagger-ui.html", "/webjars/**",
                        "/swagger-resources/**", "/v2/api-docs").authenticated()
                .anyRequest().denyAll()
                .and().httpBasic()
                .and().formLogin().disable()
                .logout().disable()
                .requestCache().disable();
    }
}
