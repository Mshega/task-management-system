package com.viwe.task_management_system.config;

import com.viwe.task_management_system.security.JwtAuthenticationEntryPoint;
import com.viwe.task_management_system.security.JwtAuthenticationFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
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
 *   <li>Public endpoints: {@code POST /api/auth/register},
 *       {@code POST /api/auth/login}, and {@code GET /actuator/health}.</li>
 *   <li>All other endpoints (including {@code /api/tasks/**} and
 *       {@code /api/users/**}) require a valid JWT.</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /**
     * Main security filter chain.
     *
     * <p>The JWT filter is inserted <em>before</em>
     * {@link UsernamePasswordAuthenticationFilter} so that each request is
     * authenticated from the Bearer token before Spring Security's own
     * authentication processing runs.
     */
    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtAuthenticationFilter jwtAuthenticationFilter,
            JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint,
            AuthenticationProvider authenticationProvider) throws Exception {
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

            .exceptionHandling(exceptions -> exceptions
                .authenticationEntryPoint(jwtAuthenticationEntryPoint)
                .accessDeniedHandler(jwtAuthenticationEntryPoint)
            )

            .authenticationProvider(authenticationProvider)

            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.POST, "/api/auth/register").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
                .requestMatchers(HttpMethod.GET, "/actuator/health").permitAll()
                .requestMatchers("/api/tasks/**").authenticated()
                .requestMatchers("/api/users/**").authenticated()
                .anyRequest().authenticated()
            )

            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * Prevents the JWT filter from also being registered as a servlet filter,
     * which would run it twice (once outside Spring Security and once inside).
     */
    @Bean
    public FilterRegistrationBean<JwtAuthenticationFilter> jwtFilterRegistration(
            JwtAuthenticationFilter filter) {
        FilterRegistrationBean<JwtAuthenticationFilter> registration =
                new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
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
    public AuthenticationProvider authenticationProvider(UserDetailsService userDetailsService,
                                                         PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
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
