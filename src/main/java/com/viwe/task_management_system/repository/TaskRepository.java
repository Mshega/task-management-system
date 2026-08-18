package com.viwe.task_management_system.repository;

import com.viwe.task_management_system.entity.Task;
import com.viwe.task_management_system.enums.TaskPriority;
import com.viwe.task_management_system.enums.TaskStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Persistence operations for {@link Task} entities.
 *
 * <p>All queries are scoped to a specific user ({@code userId}) to enforce
 * data isolation at the database level. A user should never be able to
 * retrieve or modify another user's tasks.
 *
 * <p>The service layer is responsible for:
 * <ul>
 *   <li>Extracting the authenticated user's ID from the security context.</li>
 *   <li>Passing that ID to these repository methods.</li>
 *   <li>Interpreting an empty {@link Optional} as a 404 or 403 response.</li>
 * </ul>
 *
 * <p>All list methods accept a {@link Pageable} parameter so the service and
 * controller layers can support pagination and sorting without additional
 * query variants.
 */
public interface TaskRepository extends JpaRepository<Task, Long> {

    /**
     * Returns a page of all tasks belonging to the specified user.
     *
     * <p>This is the primary list query used by the task controller.
     * Sorting and page size are controlled by the {@link Pageable} argument.
     *
     * @param userId   the ID of the owning user
     * @param pageable pagination and sorting parameters
     * @return a page of tasks owned by the user
     */
    Page<Task> findAllByUserId(Long userId, Pageable pageable);

    /**
     * Returns a page of tasks belonging to the specified user filtered by status.
     *
     * <p>Used when the client requests tasks with a specific lifecycle status
     * (e.g. all TODO tasks for the current user).
     *
     * @param userId   the ID of the owning user
     * @param status   the task status to filter by
     * @param pageable pagination and sorting parameters
     * @return a page of matching tasks
     */
    Page<Task> findAllByUserIdAndStatus(Long userId, TaskStatus status, Pageable pageable);

    /**
     * Returns a page of tasks belonging to the specified user filtered by priority.
     *
     * <p>Used when the client requests tasks with a specific priority level
     * (e.g. all URGENT tasks for the current user).
     *
     * @param userId   the ID of the owning user
     * @param priority the priority level to filter by
     * @param pageable pagination and sorting parameters
     * @return a page of matching tasks
     */
    Page<Task> findAllByUserIdAndPriority(Long userId, TaskPriority priority, Pageable pageable);

    /**
     * Finds a single task by its ID, but only if it belongs to the specified user.
     *
     * <p>Combining both conditions in one query prevents a user from reading
     * another user's task even if they know its ID. The service layer treats
     * an empty result as a 404 (task not found or not owned by this user).
     *
     * @param id     the task ID
     * @param userId the ID of the owning user
     * @return an {@link Optional} containing the task if found and owned, or empty
     */
    Optional<Task> findByIdAndUserId(Long id, Long userId);

    /**
     * Checks whether a task with the given ID exists and belongs to the specified user.
     *
     * <p>Used before a delete operation to confirm ownership without loading
     * the full entity. More efficient than {@link #findByIdAndUserId(Long, Long)}
     * when only existence needs to be verified.
     *
     * @param id     the task ID
     * @param userId the ID of the owning user
     * @return {@code true} if the task exists and is owned by this user
     */
    boolean existsByIdAndUserId(Long id, Long userId);
}
