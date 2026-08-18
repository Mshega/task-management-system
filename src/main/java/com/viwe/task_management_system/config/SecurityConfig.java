package com.viwe.task_management_system.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Foundation security configuration for the Task Management System REST API.
 *
 * <p>This configuration establishes the security baseline for a stateless,
 * token-based REST API. It is intentionally minimal at this stage:
 * authentication endpoints are opened for future implementation, and all
 * task endpoints are protected. JWT authentication will be layered on top
 * of this configuration in a later step without requiring structural changes.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /**
     * Defines the security filter chain applied to all incoming HTTP requests.
     *
     * <p>Key decisions made here:
     * <ul>
     *   <li>CSRF is disabled because this is a stateless REST API consumed by
     *       non-browser clients or a decoupled frontend. CSRF protection is
     *       only relevant for session-cookie-based flows.</li>
     *   <li>Session creation is set to STATELESS so Spring Security never
     *       creates or uses an HTTP session. Every request must carry its own
     *       credentials (JWT, once implemented).</li>
     *   <li>Form login and HTTP Basic are disabled. The API will authenticate
     *       via POST /api/auth/login returning a JWT, not via browser prompts.</li>
     *   <li>Auth endpoints (/api/auth/**) are permitted without authentication
     *       so that register and login requests can reach the controller once
     *       it is implemented.</li>
     *   <li>Actuator health endpoint is permitted so infrastructure tooling can
     *       check application liveness without credentials.</li>
     *   <li>All other requests, including /api/tasks/**, require authentication.</li>
     * </ul>
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // Disable CSRF — stateless REST API, no session cookies
            .csrf(AbstractHttpConfigurer::disable)

            // Never create or consult an HTTP session
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )

            // Disable form-based login — not appropriate for a REST API
            .formLogin(AbstractHttpConfigurer::disable)

            // Disable HTTP Basic — API will use Bearer token (JWT) authentication
            .httpBasic(AbstractHttpConfigurer::disable)

            // Define which requests are permitted and which require authentication
            .authorizeHttpRequests(auth -> auth

                // Authentication endpoints — publicly accessible so clients can register and log in
                .requestMatchers(HttpMethod.POST, "/api/auth/register").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()

                // Actuator health — allow liveness/readiness checks without credentials
                .requestMatchers("/actuator/health").permitAll()

                // Everything else requires a valid, authenticated request
                // This explicitly covers /api/tasks/** and any future protected endpoints
                .anyRequest().authenticated()
            );

        // Note: the JWT authentication filter will be inserted here in a later step
        // using http.addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)

        return http.build();
    }
}
