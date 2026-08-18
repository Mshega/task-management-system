package com.viwe.task_management_system.exception;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Minimal controller used exclusively by {@link GlobalExceptionHandlerTest}.
 *
 * <p>Each endpoint throws a specific exception type so the test can verify
 * that {@link GlobalExceptionHandler} converts it to the expected HTTP response.
 * This controller is only on the test classpath — it is never loaded in production.
 */
@RestController
@RequestMapping("/test")
class TestExceptionController {

    /** Triggers MethodArgumentNotValidException or HttpMessageNotReadableException. */
    @PostMapping("/validate")
    String validate(@Valid @RequestBody ValidatableRequest request) {
        return "ok";
    }

    /** Triggers BusinessRuleViolationException → 400. */
    @GetMapping("/business-rule")
    String businessRule() {
        throw new BusinessRuleViolationException(
                "Cannot transition task from DONE to IN_PROGRESS");
    }

    /** Triggers AccessDeniedException → 403. */
    @GetMapping("/forbidden")
    String forbidden() {
        throw new AccessDeniedException("Forbidden");
    }

    /** Triggers ResourceNotFoundException → 404. */
    @GetMapping("/resource-not-found")
    String resourceNotFound() {
        throw new ResourceNotFoundException("Task", 99);
    }

    /** Triggers DuplicateResourceException → 409. */
    @GetMapping("/duplicate")
    String duplicate() {
        throw new DuplicateResourceException("User", "email", "alice@example.com");
    }

    /** Triggers an unexpected RuntimeException → 500. */
    @GetMapping("/server-error")
    String serverError() {
        throw new RuntimeException("Something went wrong internally");
    }

    // -------------------------------------------------------------------------

    /**
     * Minimal DTO used to trigger Bean Validation in the test controller.
     * Has one required field so an empty JSON body produces a validation error.
     */
    record ValidatableRequest(
            @NotBlank(message = "Name is required") String name
    ) {}
}
