package com.viwe.task_management_system.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Request body for POST /api/auth/login.
 *
 * <p>Credentials are validated structurally here (not blank, valid email format).
 * The actual credential verification (password check against the stored hash)
 * is performed by the authentication service.
 *
 * @param email    the registered email address
 * @param password the plain-text password to verify
 */
public record LoginRequest(

        @NotBlank(message = "Email is required")
        @Email(message = "Email must be a valid email address")
        String email,

        @NotBlank(message = "Password is required")
        String password

) {}
