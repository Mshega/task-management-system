package com.viwe.task_management_system.security;

import com.viwe.task_management_system.service.JwtService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Intercepts every request and authenticates a Bearer JWT from the
 * {@code Authorization} header.
 *
 * <p>If a valid token is present, the filter:
 * <ol>
 *   <li>Extracts the user's email from the token subject claim.</li>
 *   <li>Loads the {@link UserDetails} from the database.</li>
 *   <li>Validates signature, expiry, and subject.</li>
 *   <li>Sets the authentication in the {@link SecurityContextHolder}.</li>
 * </ol>
 *
 * <p>If the header is absent, the filter continues unauthenticated and
 * Spring Security rejects protected endpoints with 401.
 *
 * <p>If a Bearer token is present but invalid or expired, the filter
 * immediately returns 401 and does not continue the chain.
 *
 * <p>This filter is registered only on the Spring Security filter chain
 * (servlet registration is disabled) so it runs exactly once per request.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);
    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String INVALID_TOKEN_MESSAGE = "Invalid or expired authentication token";

    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;
    private final JwtAuthenticationEntryPoint authenticationEntryPoint;

    public JwtAuthenticationFilter(JwtService jwtService,
                                   UserDetailsService userDetailsService,
                                   JwtAuthenticationEntryPoint authenticationEntryPoint) {
        this.jwtService = jwtService;
        this.userDetailsService = userDetailsService;
        this.authenticationEntryPoint = authenticationEntryPoint;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        String authHeader = request.getHeader(AUTHORIZATION_HEADER);

        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }

        String jwt = authHeader.substring(BEARER_PREFIX.length()).strip();
        if (jwt.isEmpty()) {
            reject(request, response);
            return;
        }

        try {
            String email = jwtService.extractEmail(jwt);

            if (email == null || email.isBlank()) {
                reject(request, response);
                return;
            }

            if (SecurityContextHolder.getContext().getAuthentication() == null) {
                UserDetails userDetails = userDetailsService.loadUserByUsername(email);

                if (!jwtService.isTokenValid(jwt, userDetails)) {
                    reject(request, response);
                    return;
                }

                UsernamePasswordAuthenticationToken authToken =
                        new UsernamePasswordAuthenticationToken(
                                userDetails,
                                null,
                                userDetails.getAuthorities());

                authToken.setDetails(
                        new WebAuthenticationDetailsSource().buildDetails(request));

                SecurityContextHolder.getContext().setAuthentication(authToken);
            }
        } catch (UsernameNotFoundException e) {
            log.debug("JWT subject does not match a user for request to {}", request.getRequestURI());
            reject(request, response);
            return;
        } catch (Exception e) {
            log.debug("JWT processing failed for request to {}: {}",
                    request.getRequestURI(), e.getMessage());
            reject(request, response);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private void reject(HttpServletRequest request, HttpServletResponse response) throws IOException {
        SecurityContextHolder.clearContext();
        authenticationEntryPoint.commence(
                request, response, new BadCredentialsException(INVALID_TOKEN_MESSAGE));
    }
}
