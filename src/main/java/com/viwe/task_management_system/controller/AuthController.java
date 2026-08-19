package com.viwe.task_management_system.controller;

import com.viwe.task_management_system.dto.request.LoginRequest;
import com.viwe.task_management_system.dto.request.RegisterRequest;
import com.viwe.task_management_system.dto.response.AuthResponse;
import com.viwe.task_management_system.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Handles user registration and login.
 *
 * <p>Both endpoints are publicly accessible (configured in {@code SecurityConfig}).
 * All business logic (duplicate checking, password hashing, JWT generation)
 * is delegated to {@link AuthService} — this controller is deliberately thin.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * POST /api/auth/register
     *
     * <p>Creates a new user account and returns a JWT so the client is
     * immediately authenticated after registration.
     *
     * @param request validated registration data
     * @return 201 Created with a {@link AuthResponse} containing the JWT
     */
    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(
            @Valid @RequestBody RegisterRequest request) {
        AuthResponse response = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * POST /api/auth/login
     *
     * <p>Authenticates an existing user and returns a JWT.
     * Invalid credentials produce a 401 response via the global exception handler.
     *
     * @param request the login credentials
     * @return 200 OK with a {@link AuthResponse} containing the JWT
     */
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(
            @Valid @RequestBody LoginRequest request) {
        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(response);
    }
}
