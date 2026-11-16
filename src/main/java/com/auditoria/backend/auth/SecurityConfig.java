package com.auditoria.backend.auth;

import java.util.List;

import jakarta.servlet.DispatcherType;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            SecurityContextRepository repository,
            Environment environment
    ) throws Exception {

        boolean desenvolvimento = environment.matchesProfiles("dev");

        http
            .securityContext(context ->
                context.securityContextRepository(repository)
            )

            .authorizeHttpRequests(auth -> {
                auth.dispatcherTypeMatchers(
                    DispatcherType.ERROR
                ).permitAll();

                // Login e cadastro não exigem sessão autenticada.
                auth.requestMatchers(
                    HttpMethod.POST,
                    "/api/auth/login",
                    "/api/auth/registro"
                ).permitAll();

                // Dados públicos necessários às telas de acesso.
                auth.requestMatchers(
                    HttpMethod.GET,
                    "/api/auth/csrf",
                    "/api/empresas/publicas"
                ).permitAll();

                // Cliente e equipe podem consultar a própria sessão.
                auth.requestMatchers(
                    HttpMethod.GET,
                    "/api/auth/me"
                ).authenticated();

                // Demais endpoints ficam restritos à equipe.
                auth.anyRequest().hasAnyRole(
                    "ADMIN",
                    "RESPONSAVEL",
                    "COLABORADOR"
                );
            })

            // Mantém a proteção CSRF também em desenvolvimento.
            .csrf(csrf -> {})

            .exceptionHandling(exceptions ->
                exceptions.authenticationEntryPoint(
                    new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)
                )
            )

            // Encerra a sessão por POST com token CSRF.
            .logout(logout -> logout
                .logoutUrl("/api/auth/logout")
                .invalidateHttpSession(true)
                .clearAuthentication(true)
                .deleteCookies("JSESSIONID")
                .logoutSuccessHandler(
                    (request, response, authentication) ->
                        response.setStatus(
                            HttpStatus.NO_CONTENT.value()
                        )
                )
                .permitAll()
            )

            .formLogin(form -> form.disable())
            .httpBasic(basic -> basic.disable());

        if (desenvolvimento) {
            CorsConfiguration cors = new CorsConfiguration();

            cors.setAllowedOrigins(List.of(
                "http://localhost:5173",
                "http://localhost:5174",
                "http://localhost:3000"
            ));

            cors.setAllowedMethods(List.of(
                "GET",
                "HEAD",
                "POST",
                "PUT",
                "PATCH",
                "DELETE",
                "OPTIONS"
            ));

            cors.setAllowedHeaders(List.of(
                "Content-Type",
                "Authorization",
                "X-CSRF-TOKEN",
                "X-XSRF-TOKEN"
            ));

            cors.setAllowCredentials(true);

            UrlBasedCorsConfigurationSource source =
                    new UrlBasedCorsConfigurationSource();

            source.registerCorsConfiguration("/api/**", cors);

            http.cors(config -> config.configurationSource(source));
        }

        return http.build();
    }
}