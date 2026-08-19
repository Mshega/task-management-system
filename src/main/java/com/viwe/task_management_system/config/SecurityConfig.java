package com.viwe.task_management_system.config;

import com.viwe.task_management_system.security.JwtAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Security configuration for the Task Management System REST API.
 *
 * <p>This class wires together:
 * <ul>
 *   <li>Stateless session management (no HTTP sessions created).</li>
 *   <li>JWT authentication filter applied before Spring Security's default
 *       username/password filter.</li>
 *   <li>BCrypt password encoder for secure password hashing.</li>
 *   <li>DAO-based authentication provider that delegates to
 *       {@link UserDetailsService} and {@link PasswordEncoder}.</li>
 *   <li>Public endpoints: {@code /api/auth/**} and {@code /actuator/health}.</li>
 *   <li>All other endpoints require a valid JWT.</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final UserDetailsService userDetailsService;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter,
                          UserDetailsService userDetailsService) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.userDetailsService = userDetailsService;
    }

    /**
     * Main security filter chain.
     *
     * <p>The JWT filter is inserted <em>before</em>
     * {@link UsernamePasswordAuthenticationFilter} so that each request is
     * authenticated from the Bearer token before Spring Security's own
     * authentication processing runs.
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

            // Disable HTTP Basic — API uses Bearer token (JWT) authentication
            .httpBasic(AbstractHttpConfigurer::disable)

            // Wire in the DAO authentication provider
            .authenticationProvider(authenticationProvider())

            // Define endpoint access rules
            .authorizeHttpRequests(auth -> auth
                // Auth endpoints — publicly accessible (no token required)
                .requestMatchers(HttpMethod.POST, "/api/auth/register").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
                // Actuator health — allow liveness checks without credentials
                .requestMatchers("/actuator/health").permitAll()
                // Everything else requires a valid JWT
                .anyRequest().authenticated()
            )

            // Insert JWT filter before the default username/password filter
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * BCrypt password encoder with default strength (10 rounds).
     * Used for hashing passwords at registration and verifying them at login.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * DAO authentication provider.
     * Loads the user via {@link UserDetailsService} and verifies the password
     * using {@link PasswordEncoder}. Used by the {@link AuthenticationManager}.
     */
    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    /**
     * Exposes the {@link AuthenticationManager} as a bean so it can be
     * injected into {@code AuthServiceImpl} for programmatic authentication
     * during login.
     */
    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}
