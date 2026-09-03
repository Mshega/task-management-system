package com.viwe.task_management_system.service.impl;

import com.viwe.task_management_system.config.TaskPageConfig;
import com.viwe.task_management_system.dto.request.CreateTaskRequest;
import com.viwe.task_management_system.dto.request.TaskFilterRequest;
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
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementation of {@link TaskService}.
 *
 * <h2>Filtering and pagination</h2>
 * <p>All list queries go through
 * {@link TaskRepository#findAllByUserIdWithFilters}, a single JPQL query that
 * accepts optional status, priority, title-substring, and due-date bounds.
 * Null parameters are silently ignored, so the query degrades gracefully from
 * "fully filtered" to "all tasks" without branching logic in the service.
 *
 * <h2>Page-size cap</h2>
 * <p>Before delegating to the repository the service replaces the client's
 * requested page size with {@code min(requested, maxPageSize)} so that no
 * single call can force a table scan of unbounded size. The cap is read from
 * {@link TaskPageConfig} and defaults to 100.
 *
 * <h2>Ownership</h2>
 * <p>Every read and mutation path enforces ownership. {@link #findOwnedTask}
 * is the single point of enforcement for get / update / complete.
 * {@link #deleteTask} uses an existence check to avoid loading the entity.
 * Neither path exposes whether a missing result is "not found" or
 * "wrong owner" — both produce a 404 to prevent task-ID enumeration.
 */
@Service
@Transactional
public class TaskServiceImpl implements TaskService {

    private final TaskRepository taskRepository;
    private final UserRepository userRepository;
    private final TaskPageConfig taskPageConfig;

    public TaskServiceImpl(TaskRepository taskRepository,
                           UserRepository userRepository,
                           TaskPageConfig taskPageConfig) {
        this.taskRepository = taskRepository;
        this.userRepository = userRepository;
        this.taskPageConfig = taskPageConfig;
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

    // ── Read (list) ──────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public Page<TaskResponse> getUserTasks(Long userId,
                                           TaskFilterRequest filter,
                                           Pageable pageable) {
        Pageable capped = capPageSize(pageable);
        return taskRepository.findAllByUserIdWithFilters(
                userId,
                filter.status(),
                filter.priority(),
                filter.title(),
                filter.dueOnOrBefore(),
                filter.dueOnOrAfter(),
                capped
        ).map(TaskResponse::from);
    }

    // ── Read (single) ────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public TaskResponse getTaskById(Long taskId, Long userId) {
        return TaskResponse.from(findOwnedTask(taskId, userId));
    }

    // ── Update ───────────────────────────────────────────────────────────────

    @Override
    public TaskResponse updateTask(Long taskId, UpdateTaskRequest request, Long userId) {
        Task task = findOwnedTask(taskId, userId);

        if (request.title() != null)       task.setTitle(request.title());
        if (request.description() != null) task.setDescription(request.description());
        if (request.status() != null) {
            assertValidTransition(task.getStatus(), request.status());
            task.setStatus(request.status());
        }
        if (request.priority() != null)    task.setPriority(request.priority());
        if (request.dueDate() != null)     task.setDueDate(request.dueDate());

        return TaskResponse.from(taskRepository.save(task));
    }

    // ── Delete ───────────────────────────────────────────────────────────────

    @Override
    public void deleteTask(Long taskId, Long userId) {
        if (!taskRepository.existsByIdAndUserId(taskId, userId)) {
            throw new ResourceNotFoundException("Task", taskId);
        }
        taskRepository.deleteById(taskId);
    }

    // ── Complete ─────────────────────────────────────────────────────────────

    @Override
    public TaskResponse completeTask(Long taskId, Long userId) {
        Task task = findOwnedTask(taskId, userId);

        if (task.getStatus() == TaskStatus.DONE) {
            throw new BusinessRuleViolationException(
                    "Cannot transition task from DONE to DONE: task is already completed");
        }
        if (task.getStatus() == TaskStatus.CANCELLED) {
            throw new BusinessRuleViolationException(
                    "Cannot transition task from CANCELLED to DONE: " +
                    "reopen the task first by setting its status to TODO");
        }

        task.setStatus(TaskStatus.DONE);
        return TaskResponse.from(taskRepository.save(task));
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    /**
     * Loads a task by ID and verifies it belongs to {@code userId}.
     * Throws {@link ResourceNotFoundException} for both "not found" and "wrong
     * owner" to prevent callers from enumerating tasks owned by other users.
     */
    private Task findOwnedTask(Long taskId, Long userId) {
        return taskRepository.findByIdAndUserId(taskId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Task", taskId));
    }

    /**
     * Returns a {@link Pageable} whose page size is capped at
     * {@link TaskPageConfig#getMaxPageSize()}. All other attributes
     * (page number, sort) are preserved unchanged.
     *
     * <p>This prevents a client from requesting an arbitrarily large page and
     * forcing a full-table scan.
     */
    private Pageable capPageSize(Pageable pageable) {
        int max = taskPageConfig.getMaxPageSize();
        if (pageable.getPageSize() <= max) {
            return pageable;
        }
        return PageRequest.of(pageable.getPageNumber(), max, pageable.getSort());
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
     * <p>Same-status updates are always a no-op and never throw.
     */
    private void assertValidTransition(TaskStatus current, TaskStatus requested) {
        if (current == requested) return;

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
