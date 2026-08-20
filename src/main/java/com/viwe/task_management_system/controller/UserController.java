package com.viwe.task_management_system.controller;

import com.viwe.task_management_system.dto.response.UserResponse;
import com.viwe.task_management_system.entity.User;
import com.viwe.task_management_system.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Authenticated user profile endpoints.
 *
 * <p>The user id is always taken from the JWT-backed principal — never from
 * the request path or body.
 */
@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    /**
     * GET /api/users/me
     *
     * @param currentUser the authenticated user injected from the security context
     * @return 200 OK with the caller's profile (password is never included)
     */
    @GetMapping("/me")
    public ResponseEntity<UserResponse> getCurrentUser(
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(userService.getUserProfile(currentUser.getId()));
    }
}
