package com.viwe.task_management_system.exception;

import com.viwe.task_management_system.config.SecurityConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for {@link GlobalExceptionHandler}.
 *
 * <p>Uses {@code @WebMvcTest} to load only the web slice (controllers, filters,
 * exception handlers). No database or Testcontainers is required. The real
 * {@link SecurityConfig} is imported so that security rules are exercised
 * authentically.
 *
 * <p>A minimal {@link TestExceptionController} is included in this test class
 * to trigger each exception type without coupling the tests to any real
 * application controller.
 */
@WebMvcTest(controllers = TestExceptionController.class)
@Import({GlobalExceptionHandler.class, SecurityConfig.class})
class GlobalExceptionHandlerTest {

    @Autowired
    private MockMvc mockMvc;

    // ── 400 Validation ───────────────────────────────────────────────────────

    @Test
    @DisplayName("POST with invalid body returns 400 VALIDATION_FAILED with field errors")
    @WithMockUser
    void whenValidationFails_returns400WithFieldErrors() throws Exception {
        // Empty JSON triggers @NotBlank failures on the test DTO
        mockMvc.perform(post("/test/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message").value("Request validation failed"))
                .andExpect(jsonPath("$.fieldErrors").isArray())
                .andExpect(jsonPath("$.fieldErrors").isNotEmpty())
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    @DisplayName("POST with malformed JSON returns 400 MALFORMED_REQUEST")
    @WithMockUser
    void whenBodyIsMalformed_returns400MalformedRequest() throws Exception {
        mockMvc.perform(post("/test/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ not valid json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("MALFORMED_REQUEST"))
                .andExpect(jsonPath("$.fieldErrors").doesNotExist());
    }

    @Test
    @DisplayName("GET that throws BusinessRuleViolationException returns 400 BUSINESS_RULE_VIOLATION")
    @WithMockUser
    void whenBusinessRuleViolated_returns400() throws Exception {
        mockMvc.perform(get("/test/business-rule"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("BUSINESS_RULE_VIOLATION"))
                .andExpect(jsonPath("$.message").value("Cannot transition task from DONE to IN_PROGRESS"))
                .andExpect(jsonPath("$.fieldErrors").doesNotExist());
    }

    // ── 401 Unauthorized ─────────────────────────────────────────────────────

    @Test
    @DisplayName("Unauthenticated request to protected endpoint is rejected")
    void whenNotAuthenticated_requestIsRejected() throws Exception {
        // Without authentication, Spring Security rejects the request.
        // Our SecurityConfig configures STATELESS sessions and disables form
        // login, so the response is a 4xx client error (401 or 403 depending
        // on how the @WebMvcTest slice initialises the security filter chain).
        // We verify the request is not permitted — not 2xx or 3xx.
        mockMvc.perform(get("/test/resource-not-found"))
                .andExpect(status().is4xxClientError());
    }

    // ── 403 Forbidden ────────────────────────────────────────────────────────

    @Test
    @DisplayName("GET that throws AccessDeniedException returns 403 FORBIDDEN")
    @WithMockUser
    void whenAccessDenied_returns403() throws Exception {
        mockMvc.perform(get("/test/forbidden"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("FORBIDDEN"))
                .andExpect(jsonPath("$.message").value("You do not have permission to perform this action"));
    }

    // ── 404 Not Found ────────────────────────────────────────────────────────

    @Test
    @DisplayName("GET that throws ResourceNotFoundException returns 404 RESOURCE_NOT_FOUND")
    @WithMockUser
    void whenResourceNotFound_returns404() throws Exception {
        mockMvc.perform(get("/test/resource-not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("RESOURCE_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Task not found with id: 99"))
                .andExpect(jsonPath("$.fieldErrors").doesNotExist());
    }

    // ── 409 Conflict ────────────────────────────────────────────────────────

    @Test
    @DisplayName("GET that throws DuplicateResourceException returns 409 DUPLICATE_RESOURCE")
    @WithMockUser
    void whenDuplicateResource_returns409() throws Exception {
        mockMvc.perform(get("/test/duplicate"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("DUPLICATE_RESOURCE"))
                .andExpect(jsonPath("$.message").value("User already exists with email: alice@example.com"))
                .andExpect(jsonPath("$.fieldErrors").doesNotExist());
    }

    // ── 500 Internal Server Error ────────────────────────────────────────────

    @Test
    @DisplayName("GET that throws an unexpected RuntimeException returns 500 INTERNAL_SERVER_ERROR")
    @WithMockUser
    void whenUnexpectedError_returns500() throws Exception {
        mockMvc.perform(get("/test/server-error"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.error").value("INTERNAL_SERVER_ERROR"))
                .andExpect(jsonPath("$.message").value("An unexpected error occurred. Please try again later."))
                // Stack trace must never be in the response
                .andExpect(jsonPath("$.trace").doesNotExist())
                .andExpect(jsonPath("$.fieldErrors").doesNotExist());
    }

    // ── Response shape ────────────────────────────────────────────────────────

    @Test
    @DisplayName("Error response always contains timestamp, status, error, and message")
    @WithMockUser
    void errorResponse_alwaysContainsCoreFields() throws Exception {
        mockMvc.perform(get("/test/resource-not-found"))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.status").exists())
                .andExpect(jsonPath("$.error").exists())
                .andExpect(jsonPath("$.message").exists());
    }
}
