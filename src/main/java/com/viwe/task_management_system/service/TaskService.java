package com.viwe.task_management_system.service;

import com.viwe.task_management_system.dto.request.CreateTaskRequest;
import com.viwe.task_management_system.dto.request.TaskFilterRequest;
import com.viwe.task_management_system.dto.request.UpdateTaskRequest;
import com.viwe.task_management_system.dto.response.TaskResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Service contract for task management operations.
 *
 * <p>All methods that read or modify tasks enforce ownership through the
 * {@code userId} parameter. The controller always derives this value from the
 * Spring Security principal — never from client-supplied input.
 */
public interface TaskService {

    /**
     * Creates a new task owned by the given user.
     *
     * @param request validated task creation data
     * @param userId  the authenticated user's ID
     * @return the created task
     */
    TaskResponse createTask(CreateTaskRequest request, Long userId);

    /**
     * Returns a paginated, optionally filtered and sorted page of tasks
     * belonging to the specified user.
     *
     * <p>All filter dimensions in {@code filter} are optional. Passing a
     * {@link TaskFilterRequest} with all-{@code null} fields returns all tasks
     * for the user (equivalent to the old no-filter call).
     *
     * <p>The service caps the page size at the configured maximum before
     * delegating to the repository, ensuring no single request can load an
     * unreasonable number of rows.
     *
     * @param userId   the authenticated user's ID
     * @param filter   optional filter/search parameters (never {@code null};
     *                 use an empty {@code TaskFilterRequest} if no filters are needed)
     * @param pageable pagination and sorting parameters from the client
     * @return a page of matching tasks
     */
    Page<TaskResponse> getUserTasks(Long userId, TaskFilterRequest filter, Pageable pageable);

    /**
     * Returns a single task by ID, verifying it belongs to the given user.
     *
     * @param taskId the task ID
     * @param userId the authenticated user's ID
     * @return the task
     * @throws com.viwe.task_management_system.exception.ResourceNotFoundException
     *         if the task does not exist or belongs to a different user
     */
    TaskResponse getTaskById(Long taskId, Long userId);

    /**
     * Applies a partial update to a task, validating status transitions and ownership.
     *
     * @param taskId  the task ID
     * @param request fields to update (only non-{@code null} fields are applied)
     * @param userId  the authenticated user's ID
     * @return the updated task
     */
    TaskResponse updateTask(Long taskId, UpdateTaskRequest request, Long userId);

    /**
     * Deletes a task, verifying ownership before removal.
     *
     * @param taskId the task ID
     * @param userId the authenticated user's ID
     */
    void deleteTask(Long taskId, Long userId);

    /**
     * Marks a task as {@code DONE}, validating that its current status permits
     * completion.
     *
     * @param taskId the task ID
     * @param userId the authenticated user's ID
     * @return the updated task
     */
    TaskResponse completeTask(Long taskId, Long userId);
}
