package com.auditoria.backend.auth;

import java.util.List;

import jakarta.servlet.DispatcherType;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.util.matcher.RequestMatcher;
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

        RequestMatcher envioReclamacao = request ->
                "POST".equals(request.getMethod())
                && "/api/reclamacao".equals(request.getServletPath());

        http
            // Armazena a autenticação na sessão HTTP
            .securityContext(context ->
                context.securityContextRepository(repository)
            )

            .authorizeHttpRequests(auth -> {
                auth.dispatcherTypeMatchers(
                    DispatcherType.ERROR
                ).permitAll();

                auth.requestMatchers(
                    "/api/auth/login",
                    "/api/auth/csrf"
                ).permitAll();

                // Liberação temporária somente no ambiente de desenvolvimento
                if (desenvolvimento) {
                    auth.requestMatchers(
                        HttpMethod.POST,
                        "/api/reclamacao"
                    ).permitAll();
                }

                auth.anyRequest().authenticated();
            })

            .csrf(csrf -> {
                // Dispensa o token somente nesse POST e no perfil dev
                if (desenvolvimento) {
                    csrf.ignoringRequestMatchers(envioReclamacao);
                }
            })

            .formLogin(form -> form.disable())
            .httpBasic(basic -> basic.disable());

        if (desenvolvimento) {
            CorsConfiguration cors = new CorsConfiguration();

            cors.setAllowedOrigins(List.of(
                "http://localhost:5173",
                "http://localhost:3000"
            ));

            cors.setAllowedMethods(List.of(
                "GET",
                "POST",
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