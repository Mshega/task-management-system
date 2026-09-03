package com.viwe.task_management_system.controller;

import com.viwe.task_management_system.dto.request.CreateTaskRequest;
import com.viwe.task_management_system.dto.request.TaskFilterRequest;
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
import org.springframework.format.annotation.DateTimeFormat;
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

import java.time.LocalDate;

/**
 * REST controller for task management operations.
 *
 * <h2>Authentication</h2>
 * <p>Every handler receives the authenticated user via
 * {@code @AuthenticationPrincipal User currentUser}. The user's ID is always
 * extracted from this injected principal — never from a URL path variable or
 * request body parameter.
 *
 * <h2>List endpoint — filtering, sorting, pagination</h2>
 * <p>{@code GET /api/tasks} accepts any combination of the following query
 * parameters:
 *
 * <table>
 *   <tr><th>Parameter</th><th>Type</th><th>Description</th></tr>
 *   <tr><td>{@code status}</td><td>TaskStatus enum</td><td>Exact-match status filter</td></tr>
 *   <tr><td>{@code priority}</td><td>TaskPriority enum</td><td>Exact-match priority filter</td></tr>
 *   <tr><td>{@code title}</td><td>String</td><td>Case-insensitive title substring search</td></tr>
 *   <tr><td>{@code dueOnOrBefore}</td><td>ISO date (yyyy-MM-dd)</td><td>Upper due-date bound (inclusive)</td></tr>
 *   <tr><td>{@code dueOnOrAfter}</td><td>ISO date (yyyy-MM-dd)</td><td>Lower due-date bound (inclusive)</td></tr>
 *   <tr><td>{@code page}</td><td>int ≥ 0</td><td>Zero-based page number (default 0)</td></tr>
 *   <tr><td>{@code size}</td><td>int ≥ 1</td><td>Items per page (default 20, max 100)</td></tr>
 *   <tr><td>{@code sort}</td><td>field,direction</td><td>e.g. {@code dueDate,asc} or {@code createdAt,desc}</td></tr>
 * </table>
 *
 * <p>Filter parameters may be combined freely.
 * Example: {@code GET /api/tasks?status=TODO&priority=HIGH&title=bug&sort=dueDate,asc}
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
     * <p>Returns a paginated, filtered, and sorted page of the authenticated
     * user's tasks. All query parameters are optional — omitting them all
     * returns the first page of all the user's tasks sorted by creation date
     * descending.
     *
     * @param status        optional exact-match status filter
     * @param priority      optional exact-match priority filter
     * @param title         optional case-insensitive title substring
     * @param dueOnOrBefore optional upper due-date bound (inclusive, ISO date)
     * @param dueOnOrAfter  optional lower due-date bound (inclusive, ISO date)
     * @param pageable      pagination and sorting (page, size, sort)
     * @param currentUser   the authenticated user (injected by Spring Security)
     * @return 200 OK with a paginated task response
     */
    @GetMapping
    public ResponseEntity<Page<TaskResponse>> getTasks(
            @RequestParam(required = false) TaskStatus status,
            @RequestParam(required = false) TaskPriority priority,
            @RequestParam(required = false) String title,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dueOnOrBefore,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dueOnOrAfter,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable,
            @AuthenticationPrincipal User currentUser) {

        TaskFilterRequest filter = new TaskFilterRequest(
                status, priority, title, dueOnOrBefore, dueOnOrAfter);

        Page<TaskResponse> tasks = taskService.getUserTasks(
                currentUser.getId(), filter, pageable);

        return ResponseEntity.ok(tasks);
    }

    /**
     * GET /api/tasks/{id}
     *
     * <p>Returns a single task. The service enforces that the task belongs to
     * the authenticated user — a task owned by someone else returns 404.
     *
     * @param id          the task ID
     * @param currentUser the authenticated user
     * @return 200 OK with the task response
     */
    @GetMapping("/{id}")
    public ResponseEntity<TaskResponse> getTaskById(
            @PathVariable Long id,
            @AuthenticationPrincipal User currentUser) {

        return ResponseEntity.ok(taskService.getTaskById(id, currentUser.getId()));
    }

    /**
     * POST /api/tasks
     *
     * <p>Creates a new task owned by the authenticated user.
     *
     * @param request     validated task creation data
     * @param currentUser the authenticated user
     * @return 201 Created with the created task response
     */
    @PostMapping
    public ResponseEntity<TaskResponse> createTask(
            @Valid @RequestBody CreateTaskRequest request,
            @AuthenticationPrincipal User currentUser) {

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(taskService.createTask(request, currentUser.getId()));
    }

    /**
     * PUT /api/tasks/{id}
     *
     * <p>Applies a partial update to a task. Only non-{@code null} fields in
     * the request are applied. The service validates status transitions and
     * ownership.
     *
     * @param id          the task ID
     * @param request     validated fields to update
     * @param currentUser the authenticated user
     * @return 200 OK with the updated task response
     */
    @PutMapping("/{id}")
    public ResponseEntity<TaskResponse> updateTask(
            @PathVariable Long id,
            @Valid @RequestBody UpdateTaskRequest request,
            @AuthenticationPrincipal User currentUser) {

        return ResponseEntity.ok(taskService.updateTask(id, request, currentUser.getId()));
    }

    /**
     * DELETE /api/tasks/{id}
     *
     * <p>Deletes a task owned by the authenticated user.
     *
     * @param id          the task ID
     * @param currentUser the authenticated user
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
     * @param currentUser the authenticated user
     * @return 200 OK with the updated task response
     */
    @PatchMapping("/{id}/complete")
    public ResponseEntity<TaskResponse> completeTask(
            @PathVariable Long id,
            @AuthenticationPrincipal User currentUser) {

        return ResponseEntity.ok(taskService.completeTask(id, currentUser.getId()));
    }
}
