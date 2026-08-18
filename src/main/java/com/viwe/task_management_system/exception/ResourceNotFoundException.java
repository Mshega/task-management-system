package com.viwe.task_management_system.exception;

/**
 * Thrown when a requested resource does not exist, or exists but does not
 * belong to the authenticated user (ownership check failure).
 *
 * <p>Both cases intentionally return 404 rather than 403 to avoid leaking
 * information about the existence of resources owned by other users.
 *
 * <p>Usage example:
 * <pre>{@code
 * taskRepository.findByIdAndUserId(id, userId)
 *     .orElseThrow(() -> new ResourceNotFoundException("Task", id));
 * }</pre>
 */
public class ResourceNotFoundException extends RuntimeException {

    /**
     * Creates an exception with a descriptive message in the form
     * "{@code <resourceName> not found with id: <id>}".
     *
     * @param resourceName the type of resource that was not found (e.g. "Task", "User")
     * @param id           the identifier that was searched for
     */
    public ResourceNotFoundException(String resourceName, Object id) {
        super(resourceName + " not found with id: " + id);
    }

    /**
     * Creates an exception with a fully custom message.
     *
     * @param message a description of what was not found
     */
    public ResourceNotFoundException(String message) {
        super(message);
    }
}
