package com.auditoria.backend.auth;

import java.nio.charset.StandardCharsets;
import java.util.List;

import jakarta.servlet.DispatcherType;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandlerImpl;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.NegatedRequestMatcher;
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
        var reformulacaoPublica = PathPatternRequestMatcher.withDefaults()
            .matcher(HttpMethod.POST, "/api/ia/reformular");
        var acessoNegadoPadrao = new AccessDeniedHandlerImpl();

        http
            .securityContext(context ->
                context.securityContextRepository(repository)
            )

            .authorizeHttpRequests(auth -> {
                auth.dispatcherTypeMatchers(
                    DispatcherType.ERROR
                ).permitAll();

                auth.requestMatchers(
                    HttpMethod.POST,
                    "/api/auth/login",
                    "/api/auth/registro"
                ).permitAll();

                auth.requestMatchers(
                    HttpMethod.GET,
                    "/api/auth/csrf",
                    "/api/empresas/publicas"
                ).permitAll();

                auth.requestMatchers(reformulacaoPublica).permitAll();

                // Continua exigindo sessão, inclusive em dev.
                auth.requestMatchers(
                    HttpMethod.GET,
                    "/api/auth/me"
                ).authenticated();

                if (desenvolvimento) {
                    // Liberação temporária para testes locais.
                    auth.anyRequest().permitAll();
                } else {
                    auth.anyRequest().hasAnyRole(
                        "ADMIN",
                        "RESPONSAVEL",
                        "COLABORADOR"
                    );
                }
            })

            .csrf(csrf -> {
                if (desenvolvimento) {
                    // Mantém a exceção de dev nas outras rotas.
                    csrf.ignoringRequestMatchers(new NegatedRequestMatcher(reformulacaoPublica));
                }
            })

            .exceptionHandling(exceptions ->
                exceptions
                    .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))
                    .accessDeniedHandler((request, response, exception) -> {
                        if (!reformulacaoPublica.matches(request)) {
                            acessoNegadoPadrao.handle(request, response, exception);
                            return;
                        }
                        response.setStatus(HttpStatus.FORBIDDEN.value());
                        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
                        response.getWriter().write("""
                            {"codigo":"IA_CSRF_INVALIDO","erro":"Token de segurança inválido. Tente novamente."}
                            """);
                    })
            )

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
