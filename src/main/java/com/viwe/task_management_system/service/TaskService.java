package com.viwe.task_management_system.service;

import com.viwe.task_management_system.dto.request.CreateTaskRequest;
import com.viwe.task_management_system.dto.request.UpdateTaskRequest;
import com.viwe.task_management_system.dto.response.TaskResponse;
import com.viwe.task_management_system.enums.TaskPriority;
import com.viwe.task_management_system.enums.TaskStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Business operations for task management.
 *
 * <p>Every method receives the authenticated user's ID so that the service
 * layer can enforce ownership rules without depending on the security context
 * directly. This also makes the service straightforward to unit-test.
 *
 * <p>Implementations must guarantee:
 * <ul>
 *   <li>A user can only read or modify their own tasks.</li>
 *   <li>Accessing a task that does not exist, or that belongs to another user,
 *       throws {@link com.viwe.task_management_system.exception.ResourceNotFoundException}.</li>
 *   <li>Invalid status transitions throw
 *       {@link com.viwe.task_management_system.exception.BusinessRuleViolationException}.</li>
 * </ul>
 */
public interface TaskService {

    /**
     * Creates a new task owned by the specified user.
     *
     * @param request the task creation data
     * @param userId  the ID of the authenticated user who will own the task
     * @return the created task as a response DTO
     */
    TaskResponse createTask(CreateTaskRequest request, Long userId);

    /**
     * Returns a paginated list of tasks belonging to the specified user.
     * Optionally filtered by status or priority — pass {@code null} to omit
     * a filter.
     *
     * @param userId   the ID of the authenticated user
     * @param status   optional status filter; {@code null} means no filter
     * @param priority optional priority filter; {@code null} means no filter
     * @param pageable pagination and sorting parameters
     * @return a page of the user's tasks
     */
    Page<TaskResponse> getUserTasks(Long userId, TaskStatus status,
                                    TaskPriority priority, Pageable pageable);

    /**
     * Returns a single task belonging to the specified user.
     *
     * @param taskId the ID of the task to retrieve
     * @param userId the ID of the authenticated user
     * @return the task as a response DTO
     * @throws com.viwe.task_management_system.exception.ResourceNotFoundException
     *         if the task does not exist or belongs to a different user
     */
    TaskResponse getTaskById(Long taskId, Long userId);

    /**
     * Updates an existing task owned by the specified user.
     * Only non-null fields in the request are applied; null fields are left unchanged.
     *
     * @param taskId  the ID of the task to update
     * @param request the fields to update
     * @param userId  the ID of the authenticated user
     * @return the updated task as a response DTO
     * @throws com.viwe.task_management_system.exception.ResourceNotFoundException
     *         if the task does not exist or belongs to a different user
     * @throws com.viwe.task_management_system.exception.BusinessRuleViolationException
     *         if the requested status transition is not permitted
     */
    TaskResponse updateTask(Long taskId, UpdateTaskRequest request, Long userId);

    /**
     * Deletes a task owned by the specified user.
     *
     * @param taskId the ID of the task to delete
     * @param userId the ID of the authenticated user
     * @throws com.viwe.task_management_system.exception.ResourceNotFoundException
     *         if the task does not exist or belongs to a different user
     */
    void deleteTask(Long taskId, Long userId);

    /**
     * Marks a task as {@code DONE}.
     *
     * <p>Convenience operation equivalent to updating the status to DONE.
     * Validates the transition from the current status before applying it.
     *
     * @param taskId the ID of the task to complete
     * @param userId the ID of the authenticated user
     * @return the updated task as a response DTO
     * @throws com.viwe.task_management_system.exception.ResourceNotFoundException
     *         if the task does not exist or belongs to a different user
     * @throws com.viwe.task_management_system.exception.BusinessRuleViolationException
     *         if the task is already DONE or CANCELLED
     */
    TaskResponse completeTask(Long taskId, Long userId);
}
