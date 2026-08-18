package com.viwe.task_management_system.service.impl;

import com.viwe.task_management_system.dto.request.CreateTaskRequest;
import com.viwe.task_management_system.dto.request.UpdateTaskRequest;
import com.viwe.task_management_system.dto.response.TaskResponse;
import com.viwe.task_management_system.entity.Task;
import com.viwe.task_management_system.entity.User;
import com.viwe.task_management_system.enums.TaskPriority;
import com.viwe.task_management_system.enums.TaskStatus;
import com.viwe.task_management_system.exception.BusinessRuleViolationException;
import com.viwe.task_management_system.exception.ResourceNotFoundException;
import com.viwe.task_management_system.repository.TaskRepository;
import com.viwe.task_management_system.repository.UserRepository;
import com.viwe.task_management_system.service.TaskService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementation of {@link TaskService}.
 *
 * <p>All methods enforce task ownership by passing the authenticated user's ID
 * to the repository. A task that does not exist OR belongs to a different user
 * always produces a {@link ResourceNotFoundException} — this intentional
 * ambiguity prevents clients from probing the existence of other users' tasks.
 *
 * <p>Status transition rules are validated before any update is persisted.
 * See {@link #assertValidTransition(TaskStatus, TaskStatus)} for the permitted
 * transitions.
 */
@Service
@Transactional
public class TaskServiceImpl implements TaskService {

    private final TaskRepository taskRepository;
    private final UserRepository userRepository;

    public TaskServiceImpl(TaskRepository taskRepository, UserRepository userRepository) {
        this.taskRepository = taskRepository;
        this.userRepository = userRepository;
    }

    // ── Create ───────────────────────────────────────────────────────────────

    @Override
    public TaskResponse createTask(CreateTaskRequest request, Long userId) {
        User owner = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));

        Task task = Task.builder()
                .title(request.title())
                .description(request.description())
                .status(request.status() != null ? request.status() : TaskStatus.TODO)
                .priority(request.priority() != null ? request.priority() : TaskPriority.MEDIUM)
                .dueDate(request.dueDate())
                .user(owner)
                .build();

        Task saved = taskRepository.save(task);
        return TaskResponse.from(saved);
    }

    // ── Read ─────────────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public Page<TaskResponse> getUserTasks(Long userId, TaskStatus status,
                                           TaskPriority priority, Pageable pageable) {
        if (status != null) {
            return taskRepository
                    .findAllByUserIdAndStatus(userId, status, pageable)
                    .map(TaskResponse::from);
        }
        if (priority != null) {
            return taskRepository
                    .findAllByUserIdAndPriority(userId, priority, pageable)
                    .map(TaskResponse::from);
        }
        return taskRepository
                .findAllByUserId(userId, pageable)
                .map(TaskResponse::from);
    }

    @Override
    @Transactional(readOnly = true)
    public TaskResponse getTaskById(Long taskId, Long userId) {
        Task task = findOwnedTask(taskId, userId);
        return TaskResponse.from(task);
    }

    // ── Update ───────────────────────────────────────────────────────────────

    @Override
    public TaskResponse updateTask(Long taskId, UpdateTaskRequest request, Long userId) {
        Task task = findOwnedTask(taskId, userId);

        if (request.title() != null) {
            task.setTitle(request.title());
        }
        if (request.description() != null) {
            task.setDescription(request.description());
        }
        if (request.status() != null) {
            assertValidTransition(task.getStatus(), request.status());
            task.setStatus(request.status());
        }
        if (request.priority() != null) {
            task.setPriority(request.priority());
        }
        if (request.dueDate() != null) {
            task.setDueDate(request.dueDate());
        }

        Task saved = taskRepository.save(task);
        return TaskResponse.from(saved);
    }

    // ── Delete ───────────────────────────────────────────────────────────────

    @Override
    public void deleteTask(Long taskId, Long userId) {
        // Use existence check to avoid loading the full entity when we only need
        // to verify ownership before deleting.
        if (!taskRepository.existsByIdAndUserId(taskId, userId)) {
            throw new ResourceNotFoundException("Task", taskId);
        }
        taskRepository.deleteById(taskId);
    }

    // ── Complete ─────────────────────────────────────────────────────────────

    @Override
    public TaskResponse completeTask(Long taskId, Long userId) {
        Task task = findOwnedTask(taskId, userId);

        assertValidTransition(task.getStatus(), TaskStatus.DONE);
        task.setStatus(TaskStatus.DONE);

        Task saved = taskRepository.save(task);
        return TaskResponse.from(saved);
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    /**
     * Loads a task by ID and verifies it belongs to the given user.
     * Throws {@link ResourceNotFoundException} if either condition is not met,
     * without distinguishing between "not found" and "wrong owner".
     */
    private Task findOwnedTask(Long taskId, Long userId) {
        return taskRepository.findByIdAndUserId(taskId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Task", taskId));
    }

    /**
     * Validates that the requested status transition is permitted.
     *
     * <p>Permitted transitions:
     * <pre>
     *   TODO         → IN_PROGRESS, CANCELLED
     *   IN_PROGRESS  → DONE, TODO, CANCELLED
     *   DONE         → TODO  (reopen)
     *   CANCELLED    → TODO  (reopen)
     * </pre>
     *
     * <p>Transitioning to the same status is always a no-op and is allowed
     * to avoid unnecessary errors on idempotent updates.
     *
     * @param current   the task's current status
     * @param requested the status the client wants to set
     * @throws BusinessRuleViolationException if the transition is not permitted
     */
    private void assertValidTransition(TaskStatus current, TaskStatus requested) {
        if (current == requested) {
            return; // no-op — idempotent, always valid
        }

        boolean valid = switch (current) {
            case TODO        -> requested == TaskStatus.IN_PROGRESS
                             || requested == TaskStatus.CANCELLED;
            case IN_PROGRESS -> requested == TaskStatus.DONE
                             || requested == TaskStatus.TODO
                             || requested == TaskStatus.CANCELLED;
            case DONE        -> requested == TaskStatus.TODO;
            case CANCELLED   -> requested == TaskStatus.TODO;
        };

        if (!valid) {
            throw new BusinessRuleViolationException(
                    "Cannot transition task from " + current + " to " + requested);
        }
    }
}
