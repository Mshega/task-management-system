package com.viwe.task_management_system.dto.response;

import com.viwe.task_management_system.entity.Task;
import com.viwe.task_management_system.enums.TaskPriority;
import com.viwe.task_management_system.enums.TaskStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Response body for all task endpoints.
 *
 * <p>Used as the return type for:
 * <ul>
 *   <li>POST   /api/tasks          (create)</li>
 *   <li>GET    /api/tasks          (list — returned inside a Page)</li>
 *   <li>GET    /api/tasks/{id}     (single task)</li>
 *   <li>PUT    /api/tasks/{id}     (update)</li>
 * </ul>
 *
 * <p>The owner is represented only by {@code userId} — the full {@link
 * com.viwe.task_management_system.entity.User} entity is never embedded in
 * the response to avoid leaking user data and to keep the payload lean.
 *
 * <p>Provides a static factory method {@link #from(Task)} to map from the
 * entity in a single place, keeping service and controller code free from
 * mapping logic.
 *
 * @param id          the unique task identifier
 * @param title       short descriptive title
 * @param description optional longer description; may be {@code null}
 * @param status      current lifecycle status
 * @param priority    current priority level
 * @param dueDate     optional due date; may be {@code null}
 * @param userId      the ID of the user who owns this task
 * @param createdAt   when the task was created
 * @param updatedAt   when the task was last modified
 */
public record TaskResponse(

        Long id,
        String title,
        String description,
        TaskStatus status,
        TaskPriority priority,
        LocalDate dueDate,
        Long userId,
        LocalDateTime createdAt,
        LocalDateTime updatedAt

) {

    /**
     * Maps a {@link Task} entity to a {@link TaskResponse}.
     *
     * <p>Accesses {@code task.getUser().getId()} which is safe as long as the
     * task entity has been fetched with its user association initialised (the
     * {@code user_id} FK column is always present in the row regardless of
     * lazy loading — the ID can be accessed without triggering a join).
     *
     * @param task the entity to map from
     * @return the corresponding response DTO
     */
    public static TaskResponse from(Task task) {
        return new TaskResponse(
                task.getId(),
                task.getTitle(),
                task.getDescription(),
                task.getStatus(),
                task.getPriority(),
                task.getDueDate(),
                task.getUser().getId(),
                task.getCreatedAt(),
                task.getUpdatedAt()
        );
    }
}
