package com.viwe.task_management_system.service;

import com.viwe.task_management_system.dto.request.LoginRequest;
import com.viwe.task_management_system.dto.request.RegisterRequest;
import com.viwe.task_management_system.dto.response.AuthResponse;

/**
 * Authentication operations: registration and login.
 *
 * <p>All authentication logic (password hashing, duplicate email checking,
 * credential verification, token generation) lives here — never in controllers.
 */
public interface AuthService {

    /**
     * Registers a new user account and returns a JWT.
     *
     * @param request validated registration data
     * @return a JWT response for the newly created user
     * @throws com.viwe.task_management_system.exception.DuplicateResourceException
     *         if a user with the given email already exists
     */
    AuthResponse register(RegisterRequest request);

    /**
     * Authenticates an existing user and returns a JWT.
     *
     * @param request the login credentials
     * @return a JWT response for the authenticated user
     * @throws org.springframework.security.core.AuthenticationException
     *         if credentials are invalid
     */
    AuthResponse login(LoginRequest request);
}
