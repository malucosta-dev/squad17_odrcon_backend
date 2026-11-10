
package com.auditoria.backend.auth;

import jakarta.servlet.DispatcherType;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            SecurityContextRepository repository
    ) throws Exception {

        http
            // Armazena a autenticação na sessão HTTP
            .securityContext(context ->
                context.securityContextRepository(repository)
            )

            // Define as permissões das rotas
            .authorizeHttpRequests(auth -> auth

                // Permite o tratamento interno de erros
                .dispatcherTypeMatchers(
                    DispatcherType.ERROR
                ).permitAll()

                // Rotas públicas de autenticação
                .requestMatchers(
                    "/api/auth/login",
                    "/api/auth/csrf"
                ).permitAll()

                // As demais rotas exigem autenticação
                .anyRequest().authenticated()
            )

            // Mantém a proteção CSRF habilitada
            .csrf(csrf -> {})

            // Desativa os métodos de login padrão
            .formLogin(form -> form.disable())
            .httpBasic(basic -> basic.disable());

        return http.build();
    }
}
