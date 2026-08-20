package com.viwe.task_management_system.controller;

import com.viwe.task_management_system.dto.request.CreateTaskRequest;
import com.viwe.task_management_system.dto.request.UpdateTaskRequest;
import com.viwe.task_management_system.dto.response.TaskResponse;
import com.viwe.task_management_system.entity.User;
import com.viwe.task_management_system.enums.TaskPriority;
import com.viwe.task_management_system.enums.TaskStatus;
import com.viwe.task_management_system.service.TaskService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for task management operations.
 *
 * <h2>Authentication design</h2>
 * <p>Every handler method receives the authenticated user via
 * {@code @AuthenticationPrincipal User currentUser}. Spring Security injects
 * the principal that was placed in the {@link org.springframework.security.core.context.SecurityContext}
 * by the authentication filter. Once the JWT filter is implemented it will
 * populate the context automatically, and this controller will require
 * <strong>zero changes</strong>.
 *
 * <p>The user's ID is always extracted from this injected principal — never
 * from a URL path variable or request body parameter. This prevents any
 * possibility of a client supplying a different user's ID to access their data.
 */
@RestController
@RequestMapping("/api/tasks")
public class TaskController {

    private final TaskService taskService;

    public TaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    /**
     * GET /api/tasks
     *
     * <p>Returns a paginated list of the authenticated user's tasks.
     * Optionally filtered by {@code status} or {@code priority}.
     * Defaults to page 0, size 20, sorted by {@code createdAt} descending.
     *
     * @param status   optional status filter
     * @param priority optional priority filter
     * @param pageable pagination and sorting (via query params: page, size, sort)
     * @param currentUser the authenticated user (injected by Spring Security)
     * @return 200 OK with a page of task responses
     */
    @GetMapping
    public ResponseEntity<Page<TaskResponse>> getTasks(
            @RequestParam(required = false) TaskStatus status,
            @RequestParam(required = false) TaskPriority priority,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable,
            @AuthenticationPrincipal User currentUser) {

        Page<TaskResponse> tasks = taskService.getUserTasks(
                currentUser.getId(), status, priority, pageable);
        return ResponseEntity.ok(tasks);
    }

    /**
     * GET /api/tasks/{id}
     *
     * <p>Returns a single task. The service enforces that the task belongs
     * to the authenticated user — a task owned by someone else returns 404.
     *
     * @param id          the task ID
     * @param currentUser the authenticated user (injected by Spring Security)
     * @return 200 OK with the task response
     */
    @GetMapping("/{id}")
    public ResponseEntity<TaskResponse> getTaskById(
            @PathVariable Long id,
            @AuthenticationPrincipal User currentUser) {

        TaskResponse task = taskService.getTaskById(id, currentUser.getId());
        return ResponseEntity.ok(task);
    }

    /**
     * POST /api/tasks
     *
     * <p>Creates a new task owned by the authenticated user.
     *
     * @param request     validated task creation data
     * @param currentUser the authenticated user (injected by Spring Security)
     * @return 201 Created with the created task response
     */
    @PostMapping
    public ResponseEntity<TaskResponse> createTask(
            @Valid @RequestBody CreateTaskRequest request,
            @AuthenticationPrincipal User currentUser) {

        TaskResponse created = taskService.createTask(request, currentUser.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * PUT /api/tasks/{id}
     *
     * <p>Updates an existing task. Only non-null fields in the request are
     * applied. The service validates status transitions and ownership.
     *
     * @param id          the task ID
     * @param request     validated fields to update
     * @param currentUser the authenticated user (injected by Spring Security)
     * @return 200 OK with the updated task response
     */
    @PutMapping("/{id}")
    public ResponseEntity<TaskResponse> updateTask(
            @PathVariable Long id,
            @Valid @RequestBody UpdateTaskRequest request,
            @AuthenticationPrincipal User currentUser) {

        TaskResponse updated = taskService.updateTask(id, request, currentUser.getId());
        return ResponseEntity.ok(updated);
    }

    /**
     * DELETE /api/tasks/{id}
     *
     * <p>Deletes a task owned by the authenticated user.
     *
     * @param id          the task ID
     * @param currentUser the authenticated user (injected by Spring Security)
     * @return 204 No Content
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTask(
            @PathVariable Long id,
            @AuthenticationPrincipal User currentUser) {

        taskService.deleteTask(id, currentUser.getId());
        return ResponseEntity.noContent().build();
    }

    /**
     * PATCH /api/tasks/{id}/complete
     *
     * <p>Marks a task as DONE. Uses PATCH because it is a partial state
     * change on an existing resource, not a full replacement.
     *
     * @param id          the task ID
     * @param currentUser the authenticated user (injected by Spring Security)
     * @return 200 OK with the updated task response
     */
    @PatchMapping("/{id}/complete")
    public ResponseEntity<TaskResponse> completeTask(
            @PathVariable Long id,
            @AuthenticationPrincipal User currentUser) {

        TaskResponse completed = taskService.completeTask(id, currentUser.getId());
        return ResponseEntity.ok(completed);
    }
}
