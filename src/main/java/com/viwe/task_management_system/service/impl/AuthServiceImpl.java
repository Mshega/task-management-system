package com.viwe.task_management_system.service.impl;

import com.viwe.task_management_system.dto.request.LoginRequest;
import com.viwe.task_management_system.dto.request.RegisterRequest;
import com.viwe.task_management_system.dto.response.AuthResponse;
import com.viwe.task_management_system.entity.User;
import com.viwe.task_management_system.enums.Role;
import com.viwe.task_management_system.exception.DuplicateResourceException;
import com.viwe.task_management_system.repository.UserRepository;
import com.viwe.task_management_system.service.AuthService;
import com.viwe.task_management_system.service.JwtService;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementation of {@link AuthService}.
 *
 * <p>Uses Spring Security's {@link AuthenticationManager} for credential
 * verification during login, which delegates to the configured
 * {@link org.springframework.security.core.userdetails.UserDetailsService}
 * and {@link PasswordEncoder}. This keeps the authentication architecture
 * clean and consistent with Spring Security conventions.
 *
 * <p>Passwords are always BCrypt-hashed before storage.
 * Plain-text passwords are never logged, stored, or returned.
 */
@Service
@Transactional
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;

    public AuthServiceImpl(UserRepository userRepository,
                           PasswordEncoder passwordEncoder,
                           JwtService jwtService,
                           AuthenticationManager authenticationManager) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.authenticationManager = authenticationManager;
    }

    // ── Register ─────────────────────────────────────────────────────────────

    @Override
    public AuthResponse register(RegisterRequest request) {
        // Normalise email to lower case for consistent uniqueness checks
        String email = request.email().toLowerCase().strip();

        if (userRepository.existsByEmail(email)) {
            throw new DuplicateResourceException("User", "email", email);
        }

        User user = User.builder()
                .firstName(request.firstName().strip())
                .lastName(request.lastName().strip())
                .email(email)
                // Hash the password — plain text is never persisted
                .password(passwordEncoder.encode(request.password()))
                .role(Role.USER)
                .build();

        userRepository.save(user);

        String token = jwtService.generateToken(user);
        return AuthResponse.of(token);
    }

    // ── Login ─────────────────────────────────────────────────────────────────

    @Override
    public AuthResponse login(LoginRequest request) {
        // Delegate credential verification to Spring Security's AuthenticationManager.
        // This calls UserDetailsService.loadUserByUsername() and verifies the password
        // using the configured PasswordEncoder. Throws AuthenticationException on failure.
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.email().toLowerCase().strip(),
                        request.password()
                )
        );

        // The principal returned by AuthenticationManager is our User entity
        // (since User implements UserDetails and is what loadUserByUsername returns)
        User user = (User) authentication.getPrincipal();

        String token = jwtService.generateToken(user);
        return AuthResponse.of(token);
    }
}
