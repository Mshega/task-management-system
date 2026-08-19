package com.viwe.task_management_system.controller;

import com.viwe.task_management_system.config.SecurityConfig;
import com.viwe.task_management_system.dto.request.LoginRequest;
import com.viwe.task_management_system.dto.request.RegisterRequest;
import com.viwe.task_management_system.dto.response.AuthResponse;
import com.viwe.task_management_system.exception.DuplicateResourceException;
import com.viwe.task_management_system.exception.GlobalExceptionHandler;
import com.viwe.task_management_system.service.AuthService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Slice tests for {@link AuthController}.
 *
 * <p>Auth endpoints are public — no {@code @WithMockUser} needed.
 * The {@link AuthService} is mocked; these tests verify that the
 * controller correctly delegates, validates input, and returns
 * the right HTTP status codes and response shapes.
 */
@WebMvcTest(controllers = AuthController.class)
@Import({GlobalExceptionHandler.class, SecurityConfig.class})
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;

    // ── Required beans for SecurityConfig ────────────────────────────────────
    // SecurityConfig depends on JwtAuthenticationFilter and UserDetailsService.
    // In a @WebMvcTest slice these must be provided as mocks.
    @MockitoBean
    private com.viwe.task_management_system.security.JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockitoBean
    private org.springframework.security.core.userdetails.UserDetailsService userDetailsService;

    @MockitoBean
    private com.viwe.task_management_system.service.JwtService jwtService;

    // ── POST /api/auth/register ───────────────────────────────────────────────

    @Test
    @DisplayName("POST /api/auth/register with valid request returns 201 with token")
    void register_withValidRequest_returns201WithToken() throws Exception {
        AuthResponse authResponse = AuthResponse.of("mock.jwt.token");
        given(authService.register(any(RegisterRequest.class))).willReturn(authResponse);

        String body = """
                {
                  "firstName": "Alice",
                  "lastName": "Smith",
                  "email": "alice@example.com",
                  "password": "securePass1"
                }
                """;

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").value("mock.jwt.token"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"));
    }

    @Test
    @DisplayName("POST /api/auth/register with missing fields returns 400 VALIDATION_FAILED")
    void register_withMissingFields_returns400() throws Exception {
        String body = """
                {
                  "email": "alice@example.com"
                }
                """;

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors").isArray());
    }

    @Test
    @DisplayName("POST /api/auth/register with invalid email returns 400 VALIDATION_FAILED")
    void register_withInvalidEmail_returns400() throws Exception {
        String body = """
                {
                  "firstName": "Alice",
                  "lastName": "Smith",
                  "email": "not-an-email",
                  "password": "securePass1"
                }
                """;

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("POST /api/auth/register with password too short returns 400")
    void register_withShortPassword_returns400() throws Exception {
        String body = """
                {
                  "firstName": "Alice",
                  "lastName": "Smith",
                  "email": "alice@example.com",
                  "password": "short"
                }
                """;

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("POST /api/auth/register with duplicate email returns 409 DUPLICATE_RESOURCE")
    void register_withDuplicateEmail_returns409() throws Exception {
        given(authService.register(any(RegisterRequest.class)))
                .willThrow(new DuplicateResourceException("User", "email", "alice@example.com"));

        String body = """
                {
                  "firstName": "Alice",
                  "lastName": "Smith",
                  "email": "alice@example.com",
                  "password": "securePass1"
                }
                """;

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("DUPLICATE_RESOURCE"))
                .andExpect(jsonPath("$.message").value(
                        "User already exists with email: alice@example.com"));
    }

    @Test
    @DisplayName("POST /api/auth/register response never contains password field")
    void register_responseNeverContainsPassword() throws Exception {
        AuthResponse authResponse = AuthResponse.of("mock.jwt.token");
        given(authService.register(any(RegisterRequest.class))).willReturn(authResponse);

        String body = """
                {
                  "firstName": "Alice",
                  "lastName": "Smith",
                  "email": "alice@example.com",
                  "password": "securePass1"
                }
                """;

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    // ── POST /api/auth/login ──────────────────────────────────────────────────

    @Test
    @DisplayName("POST /api/auth/login with valid credentials returns 200 with token")
    void login_withValidCredentials_returns200WithToken() throws Exception {
        AuthResponse authResponse = AuthResponse.of("mock.jwt.token");
        given(authService.login(any(LoginRequest.class))).willReturn(authResponse);

        String body = """
                {
                  "email": "alice@example.com",
                  "password": "securePass1"
                }
                """;

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("mock.jwt.token"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"));
    }

    @Test
    @DisplayName("POST /api/auth/login with invalid credentials returns 401 UNAUTHORIZED")
    void login_withInvalidCredentials_returns401() throws Exception {
        given(authService.login(any(LoginRequest.class)))
                .willThrow(new BadCredentialsException("Bad credentials"));

        String body = """
                {
                  "email": "alice@example.com",
                  "password": "wrongpassword"
                }
                """;

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("POST /api/auth/login with missing password returns 400 VALIDATION_FAILED")
    void login_withMissingPassword_returns400() throws Exception {
        String body = """
                {
                  "email": "alice@example.com"
                }
                """;

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("POST /api/auth/login response never contains password field")
    void login_responseNeverContainsPassword() throws Exception {
        AuthResponse authResponse = AuthResponse.of("mock.jwt.token");
        given(authService.login(any(LoginRequest.class))).willReturn(authResponse);

        String body = """
                {
                  "email": "alice@example.com",
                  "password": "securePass1"
                }
                """;

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }
}
