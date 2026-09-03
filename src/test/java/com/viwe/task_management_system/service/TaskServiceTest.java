package com.viwe.task_management_system.service;

import com.viwe.task_management_system.config.TaskPageConfig;
import com.viwe.task_management_system.dto.request.CreateTaskRequest;
import com.viwe.task_management_system.dto.request.TaskFilterRequest;
import com.viwe.task_management_system.dto.request.UpdateTaskRequest;
import com.viwe.task_management_system.dto.response.TaskResponse;
import com.viwe.task_management_system.entity.Task;
import com.viwe.task_management_system.entity.User;
import com.viwe.task_management_system.enums.Role;
import com.viwe.task_management_system.enums.TaskPriority;
import com.viwe.task_management_system.enums.TaskStatus;
import com.viwe.task_management_system.exception.BusinessRuleViolationException;
import com.viwe.task_management_system.exception.ResourceNotFoundException;
import com.viwe.task_management_system.repository.TaskRepository;
import com.viwe.task_management_system.repository.UserRepository;
import com.viwe.task_management_system.service.impl.TaskServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

/**
 * Pure unit tests for {@link TaskServiceImpl}.
 *
 * <p>No Spring context is loaded. All dependencies are mocked with Mockito.
 * {@link TaskPageConfig} is provided as a real instance (not a mock) so that
 * page-size-cap logic is exercised without over-specifying the internals.
 *
 * <p>Test scope:
 * <ul>
 *   <li>CRUD operations and their business rules</li>
 *   <li>Filtering and pagination delegation to the repository</li>
 *   <li>Page-size capping behaviour</li>
 *   <li>Ownership enforcement across all operations</li>
 *   <li>Status transition matrix</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class TaskServiceTest {

    @Mock
    private TaskRepository taskRepository;

    @Mock
    private UserRepository userRepository;

    // Real instance — we want actual capping behaviour in the tests.
    // @InjectMocks cannot construct TaskServiceImpl with a non-mocked dependency,
    // so we construct the service manually in setUp().
    private TaskPageConfig taskPageConfig;
    private TaskServiceImpl taskService;

    private User owner;
    private User otherUser;
    private Task task;

    @BeforeEach
    void setUp() {
        taskPageConfig = new TaskPageConfig();
        taskPageConfig.setMaxPageSize(100);
        taskPageConfig.setDefaultPageSize(20);

        taskService = new TaskServiceImpl(taskRepository, userRepository, taskPageConfig);

        owner = User.builder()
                .id(1L)
                .firstName("Alice")
                .lastName("Smith")
                .email("alice@example.com")
                .password("hashed")
                .role(Role.USER)
                .build();

        otherUser = User.builder()
                .id(2L)
                .firstName("Bob")
                .lastName("Jones")
                .email("bob@example.com")
                .password("hashed")
                .role(Role.USER)
                .build();

        task = Task.builder()
                .id(10L)
                .title("Write tests")
                .description("Cover service layer")
                .status(TaskStatus.TODO)
                .priority(TaskPriority.MEDIUM)
                .dueDate(LocalDate.now().plusDays(7))
                .user(owner)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Builds an empty filter (all-null fields). */
    private static TaskFilterRequest emptyFilter() {
        return new TaskFilterRequest(null, null, null, null, null);
    }

    // ── createTask ────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("createTask")
    class CreateTask {

        @Test
        @DisplayName("creates and returns a task with explicit status and priority")
        void createTask_withExplicitFields_savesAndReturns() {
            CreateTaskRequest request = new CreateTaskRequest(
                    "New task", "Details", TaskStatus.IN_PROGRESS,
                    TaskPriority.HIGH, null);

            given(userRepository.findById(1L)).willReturn(Optional.of(owner));
            given(taskRepository.save(any(Task.class))).willReturn(task);

            TaskResponse response = taskService.createTask(request, 1L);

            assertThat(response).isNotNull();
            assertThat(response.id()).isEqualTo(10L);
            then(taskRepository).should().save(any(Task.class));
        }

        @Test
        @DisplayName("applies default TODO status when request status is null")
        void createTask_withNullStatus_defaultsTodo() {
            CreateTaskRequest request = new CreateTaskRequest(
                    "Task", null, null, null, null);

            given(userRepository.findById(1L)).willReturn(Optional.of(owner));
            given(taskRepository.save(any(Task.class))).willReturn(task);

            taskService.createTask(request, 1L);

            ArgumentCaptor<Task> captor = ArgumentCaptor.forClass(Task.class);
            then(taskRepository).should().save(captor.capture());
            assertThat(captor.getValue().getStatus()).isEqualTo(TaskStatus.TODO);
        }

        @Test
        @DisplayName("applies default MEDIUM priority when request priority is null")
        void createTask_withNullPriority_defaultsMedium() {
            CreateTaskRequest request = new CreateTaskRequest(
                    "Task", null, null, null, null);

            given(userRepository.findById(1L)).willReturn(Optional.of(owner));
            given(taskRepository.save(any(Task.class))).willReturn(task);

            taskService.createTask(request, 1L);

            ArgumentCaptor<Task> captor = ArgumentCaptor.forClass(Task.class);
            then(taskRepository).should().save(captor.capture());
            assertThat(captor.getValue().getPriority()).isEqualTo(TaskPriority.MEDIUM);
        }

        @Test
        @DisplayName("throws ResourceNotFoundException when user does not exist")
        void createTask_withUnknownUser_throwsNotFound() {
            CreateTaskRequest request = new CreateTaskRequest(
                    "Task", null, null, null, null);

            given(userRepository.findById(99L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> taskService.createTask(request, 99L))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("User")
                    .hasMessageContaining("99");

            then(taskRepository).should(never()).save(any());
        }
    }

    // ── getUserTasks — filtering and pagination ───────────────────────────────

    @Nested
    @DisplayName("getUserTasks — filtering and pagination")
    class GetUserTasksFiltering {

        @Test
        @DisplayName("empty filter delegates to repository with all-null params")
        void emptyFilter_passesAllNullsToRepository() {
            Pageable pageable = PageRequest.of(0, 10);
            given(taskRepository.findAllByUserIdWithFilters(
                    eq(1L), isNull(), isNull(), isNull(), isNull(), isNull(),
                    any(Pageable.class)))
                    .willReturn(new PageImpl<>(List.of(task)));

            Page<TaskResponse> result = taskService.getUserTasks(1L, emptyFilter(), pageable);

            assertThat(result.getTotalElements()).isEqualTo(1);
            assertThat(result.getContent().get(0).id()).isEqualTo(10L);
        }

        @Test
        @DisplayName("status filter passes status to repository, others null")
        void statusFilter_passesStatusToRepository() {
            Pageable pageable = PageRequest.of(0, 10);
            TaskFilterRequest filter = new TaskFilterRequest(
                    TaskStatus.TODO, null, null, null, null);

            given(taskRepository.findAllByUserIdWithFilters(
                    eq(1L), eq(TaskStatus.TODO), isNull(), isNull(), isNull(), isNull(),
                    any(Pageable.class)))
                    .willReturn(new PageImpl<>(List.of(task)));

            Page<TaskResponse> result = taskService.getUserTasks(1L, filter, pageable);

            assertThat(result.getContent()).hasSize(1);
            then(taskRepository).should().findAllByUserIdWithFilters(
                    eq(1L), eq(TaskStatus.TODO), isNull(), isNull(), isNull(), isNull(),
                    any(Pageable.class));
        }

        @Test
        @DisplayName("priority filter passes priority to repository, others null")
        void priorityFilter_passesPriorityToRepository() {
            Pageable pageable = PageRequest.of(0, 10);
            TaskFilterRequest filter = new TaskFilterRequest(
                    null, TaskPriority.HIGH, null, null, null);

            given(taskRepository.findAllByUserIdWithFilters(
                    eq(1L), isNull(), eq(TaskPriority.HIGH), isNull(), isNull(), isNull(),
                    any(Pageable.class)))
                    .willReturn(new PageImpl<>(List.of(task)));

            taskService.getUserTasks(1L, filter, pageable);

            then(taskRepository).should().findAllByUserIdWithFilters(
                    eq(1L), isNull(), eq(TaskPriority.HIGH), isNull(), isNull(), isNull(),
                    any(Pageable.class));
        }

        @Test
        @DisplayName("title filter normalises blank to null — does not pass whitespace to repository")
        void titleFilter_blankTitle_normalisedToNull() {
            // TaskFilterRequest constructor strips blank titles to null
            TaskFilterRequest filter = new TaskFilterRequest(
                    null, null, "   ", null, null);

            assertThat(filter.title()).isNull();
        }

        @Test
        @DisplayName("title filter passes the stripped title string to repository")
        void titleFilter_nonBlankTitle_passedToRepository() {
            Pageable pageable = PageRequest.of(0, 10);
            TaskFilterRequest filter = new TaskFilterRequest(
                    null, null, "  tests  ", null, null);

            // Title should have been stripped to "tests"
            assertThat(filter.title()).isEqualTo("tests");

            given(taskRepository.findAllByUserIdWithFilters(
                    eq(1L), isNull(), isNull(), eq("tests"), isNull(), isNull(),
                    any(Pageable.class)))
                    .willReturn(new PageImpl<>(List.of(task)));

            Page<TaskResponse> result = taskService.getUserTasks(1L, filter, pageable);

            assertThat(result.getContent()).hasSize(1);
        }

        @Test
        @DisplayName("dueOnOrBefore filter is forwarded to repository")
        void dueOnOrBeforeFilter_passedToRepository() {
            Pageable pageable = PageRequest.of(0, 10);
            LocalDate bound = LocalDate.now().plusDays(5);
            TaskFilterRequest filter = new TaskFilterRequest(
                    null, null, null, bound, null);

            given(taskRepository.findAllByUserIdWithFilters(
                    eq(1L), isNull(), isNull(), isNull(), eq(bound), isNull(),
                    any(Pageable.class)))
                    .willReturn(new PageImpl<>(List.of(task)));

            taskService.getUserTasks(1L, filter, pageable);

            then(taskRepository).should().findAllByUserIdWithFilters(
                    eq(1L), isNull(), isNull(), isNull(), eq(bound), isNull(),
                    any(Pageable.class));
        }

        @Test
        @DisplayName("dueOnOrAfter filter is forwarded to repository")
        void dueOnOrAfterFilter_passedToRepository() {
            Pageable pageable = PageRequest.of(0, 10);
            LocalDate bound = LocalDate.now();
            TaskFilterRequest filter = new TaskFilterRequest(
                    null, null, null, null, bound);

            given(taskRepository.findAllByUserIdWithFilters(
                    eq(1L), isNull(), isNull(), isNull(), isNull(), eq(bound),
                    any(Pageable.class)))
                    .willReturn(new PageImpl<>(List.of(task)));

            taskService.getUserTasks(1L, filter, pageable);

            then(taskRepository).should().findAllByUserIdWithFilters(
                    eq(1L), isNull(), isNull(), isNull(), isNull(), eq(bound),
                    any(Pageable.class));
        }

        @Test
        @DisplayName("all filters combined are all forwarded to the repository")
        void allFiltersCombined_allPassedToRepository() {
            Pageable pageable = PageRequest.of(0, 10);
            LocalDate before = LocalDate.now().plusDays(10);
            LocalDate after  = LocalDate.now();
            TaskFilterRequest filter = new TaskFilterRequest(
                    TaskStatus.TODO, TaskPriority.HIGH, "bug", before, after);

            given(taskRepository.findAllByUserIdWithFilters(
                    eq(1L), eq(TaskStatus.TODO), eq(TaskPriority.HIGH),
                    eq("bug"), eq(before), eq(after),
                    any(Pageable.class)))
                    .willReturn(new PageImpl<>(List.of(task)));

            Page<TaskResponse> result = taskService.getUserTasks(1L, filter, pageable);

            assertThat(result.getContent()).hasSize(1);
            then(taskRepository).should().findAllByUserIdWithFilters(
                    eq(1L), eq(TaskStatus.TODO), eq(TaskPriority.HIGH),
                    eq("bug"), eq(before), eq(after),
                    any(Pageable.class));
        }
    }

    // ── getUserTasks — page-size capping ──────────────────────────────────────

    @Nested
    @DisplayName("getUserTasks — page-size capping")
    class PageSizeCapping {

        @Test
        @DisplayName("page size within limit passes through unchanged")
        void pageSizeWithinLimit_passedThrough() {
            Pageable pageable = PageRequest.of(0, 50); // max is 100
            given(taskRepository.findAllByUserIdWithFilters(
                    any(), any(), any(), any(), any(), any(),
                    any(Pageable.class)))
                    .willReturn(new PageImpl<>(List.of()));

            taskService.getUserTasks(1L, emptyFilter(), pageable);

            ArgumentCaptor<Pageable> cap = ArgumentCaptor.forClass(Pageable.class);
            then(taskRepository).should().findAllByUserIdWithFilters(
                    any(), any(), any(), any(), any(), any(), cap.capture());
            assertThat(cap.getValue().getPageSize()).isEqualTo(50);
        }

        @Test
        @DisplayName("page size exactly at limit passes through unchanged")
        void pageSizeAtLimit_passedThrough() {
            Pageable pageable = PageRequest.of(0, 100);
            given(taskRepository.findAllByUserIdWithFilters(
                    any(), any(), any(), any(), any(), any(),
                    any(Pageable.class)))
                    .willReturn(new PageImpl<>(List.of()));

            taskService.getUserTasks(1L, emptyFilter(), pageable);

            ArgumentCaptor<Pageable> cap = ArgumentCaptor.forClass(Pageable.class);
            then(taskRepository).should().findAllByUserIdWithFilters(
                    any(), any(), any(), any(), any(), any(), cap.capture());
            assertThat(cap.getValue().getPageSize()).isEqualTo(100);
        }

        @Test
        @DisplayName("page size above limit is silently capped to maxPageSize")
        void pageSizeAboveLimit_isCapped() {
            Pageable pageable = PageRequest.of(0, 500); // exceeds max of 100
            given(taskRepository.findAllByUserIdWithFilters(
                    any(), any(), any(), any(), any(), any(),
                    any(Pageable.class)))
                    .willReturn(new PageImpl<>(List.of()));

            taskService.getUserTasks(1L, emptyFilter(), pageable);

            ArgumentCaptor<Pageable> cap = ArgumentCaptor.forClass(Pageable.class);
            then(taskRepository).should().findAllByUserIdWithFilters(
                    any(), any(), any(), any(), any(), any(), cap.capture());
            assertThat(cap.getValue().getPageSize()).isEqualTo(100);
        }

        @Test
        @DisplayName("page number and sort are preserved when size is capped")
        void capping_preservesPageNumberAndSort() {
            Sort sort = Sort.by(Sort.Direction.ASC, "dueDate");
            Pageable pageable = PageRequest.of(3, 9999, sort);

            given(taskRepository.findAllByUserIdWithFilters(
                    any(), any(), any(), any(), any(), any(),
                    any(Pageable.class)))
                    .willReturn(new PageImpl<>(List.of()));

            taskService.getUserTasks(1L, emptyFilter(), pageable);

            ArgumentCaptor<Pageable> cap = ArgumentCaptor.forClass(Pageable.class);
            then(taskRepository).should().findAllByUserIdWithFilters(
                    any(), any(), any(), any(), any(), any(), cap.capture());

            Pageable captured = cap.getValue();
            assertThat(captured.getPageSize()).isEqualTo(100);
            assertThat(captured.getPageNumber()).isEqualTo(3);
            assertThat(captured.getSort()).isEqualTo(sort);
        }

        @Test
        @DisplayName("custom maxPageSize of 5 caps any larger request to 5")
        void customMaxPageSize_capsToConfiguredValue() {
            // Override the config for this test
            taskPageConfig.setMaxPageSize(5);

            Pageable pageable = PageRequest.of(0, 50);
            given(taskRepository.findAllByUserIdWithFilters(
                    any(), any(), any(), any(), any(), any(),
                    any(Pageable.class)))
                    .willReturn(new PageImpl<>(List.of()));

            taskService.getUserTasks(1L, emptyFilter(), pageable);

            ArgumentCaptor<Pageable> cap = ArgumentCaptor.forClass(Pageable.class);
            then(taskRepository).should().findAllByUserIdWithFilters(
                    any(), any(), any(), any(), any(), any(), cap.capture());
            assertThat(cap.getValue().getPageSize()).isEqualTo(5);
        }
    }

    // ── getTaskById ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("getTaskById")
    class GetTaskById {

        @Test
        @DisplayName("returns the task when it belongs to the user")
        void getTaskById_whenOwned_returnsTask() {
            given(taskRepository.findByIdAndUserId(10L, 1L)).willReturn(Optional.of(task));

            TaskResponse response = taskService.getTaskById(10L, 1L);

            assertThat(response.id()).isEqualTo(10L);
            assertThat(response.title()).isEqualTo("Write tests");
            assertThat(response.userId()).isEqualTo(1L);
        }

        @Test
        @DisplayName("throws ResourceNotFoundException when task does not exist")
        void getTaskById_whenNotFound_throwsNotFound() {
            given(taskRepository.findByIdAndUserId(99L, 1L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> taskService.getTaskById(99L, 1L))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Task")
                    .hasMessageContaining("99");
        }

        @Test
        @DisplayName("throws ResourceNotFoundException when task belongs to another user")
        void getTaskById_whenOwnedByOtherUser_throwsNotFound() {
            given(taskRepository.findByIdAndUserId(10L, 2L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> taskService.getTaskById(10L, 2L))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    // ── updateTask ────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("updateTask")
    class UpdateTask {

        @Test
        @DisplayName("updates only non-null fields and returns updated task")
        void updateTask_withPartialFields_updatesOnlyNonNull() {
            UpdateTaskRequest request = new UpdateTaskRequest(
                    "Updated title", null, null, TaskPriority.HIGH, null);

            given(taskRepository.findByIdAndUserId(10L, 1L)).willReturn(Optional.of(task));
            given(taskRepository.save(any(Task.class))).willReturn(task);

            taskService.updateTask(10L, request, 1L);

            ArgumentCaptor<Task> captor = ArgumentCaptor.forClass(Task.class);
            then(taskRepository).should().save(captor.capture());
            Task saved = captor.getValue();
            assertThat(saved.getTitle()).isEqualTo("Updated title");
            assertThat(saved.getPriority()).isEqualTo(TaskPriority.HIGH);
            assertThat(saved.getDescription()).isEqualTo("Cover service layer");
        }

        @Test
        @DisplayName("allows valid status transition TODO → IN_PROGRESS")
        void updateTask_validTransition_succeeds() {
            UpdateTaskRequest request = new UpdateTaskRequest(
                    null, null, TaskStatus.IN_PROGRESS, null, null);

            given(taskRepository.findByIdAndUserId(10L, 1L)).willReturn(Optional.of(task));
            given(taskRepository.save(any(Task.class))).willReturn(task);

            taskService.updateTask(10L, request, 1L);

            ArgumentCaptor<Task> captor = ArgumentCaptor.forClass(Task.class);
            then(taskRepository).should().save(captor.capture());
            assertThat(captor.getValue().getStatus()).isEqualTo(TaskStatus.IN_PROGRESS);
        }

        @Test
        @DisplayName("throws BusinessRuleViolationException for invalid transition TODO → DONE")
        void updateTask_invalidTransition_throwsBusinessRuleViolation() {
            UpdateTaskRequest request = new UpdateTaskRequest(
                    null, null, TaskStatus.DONE, null, null);

            given(taskRepository.findByIdAndUserId(10L, 1L)).willReturn(Optional.of(task));

            assertThatThrownBy(() -> taskService.updateTask(10L, request, 1L))
                    .isInstanceOf(BusinessRuleViolationException.class)
                    .hasMessageContaining("TODO")
                    .hasMessageContaining("DONE");

            then(taskRepository).should(never()).save(any());
        }

        @Test
        @DisplayName("throws ResourceNotFoundException when task not found for update")
        void updateTask_notFound_throwsNotFound() {
            UpdateTaskRequest request = new UpdateTaskRequest(
                    "title", null, null, null, null);

            given(taskRepository.findByIdAndUserId(99L, 1L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> taskService.updateTask(99L, request, 1L))
                    .isInstanceOf(ResourceNotFoundException.class);

            then(taskRepository).should(never()).save(any());
        }

        @Test
        @DisplayName("same-status update is a no-op and does not throw")
        void updateTask_sameStatus_isNoOp() {
            UpdateTaskRequest request = new UpdateTaskRequest(
                    null, null, TaskStatus.TODO, null, null);

            given(taskRepository.findByIdAndUserId(10L, 1L)).willReturn(Optional.of(task));
            given(taskRepository.save(any(Task.class))).willReturn(task);

            taskService.updateTask(10L, request, 1L);

            then(taskRepository).should().save(any());
        }
    }

    // ── deleteTask ────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("deleteTask")
    class DeleteTask {

        @Test
        @DisplayName("deletes the task when it belongs to the user")
        void deleteTask_whenOwned_deletesSuccessfully() {
            given(taskRepository.existsByIdAndUserId(10L, 1L)).willReturn(true);

            taskService.deleteTask(10L, 1L);

            then(taskRepository).should().deleteById(10L);
        }

        @Test
        @DisplayName("throws ResourceNotFoundException when task does not exist")
        void deleteTask_whenNotFound_throwsNotFound() {
            given(taskRepository.existsByIdAndUserId(99L, 1L)).willReturn(false);

            assertThatThrownBy(() -> taskService.deleteTask(99L, 1L))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("99");

            then(taskRepository).should(never()).deleteById(any());
        }

        @Test
        @DisplayName("throws ResourceNotFoundException when task belongs to another user")
        void deleteTask_whenOwnedByOtherUser_throwsNotFound() {
            given(taskRepository.existsByIdAndUserId(10L, 2L)).willReturn(false);

            assertThatThrownBy(() -> taskService.deleteTask(10L, 2L))
                    .isInstanceOf(ResourceNotFoundException.class);

            then(taskRepository).should(never()).deleteById(any());
        }
    }

    // ── completeTask ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("completeTask")
    class CompleteTask {

        @Test
        @DisplayName("marks a TODO task as DONE")
        void completeTask_fromTodo_setsDone() {
            given(taskRepository.findByIdAndUserId(10L, 1L)).willReturn(Optional.of(task));
            given(taskRepository.save(any(Task.class))).willReturn(task);

            taskService.completeTask(10L, 1L);

            ArgumentCaptor<Task> captor = ArgumentCaptor.forClass(Task.class);
            then(taskRepository).should().save(captor.capture());
            assertThat(captor.getValue().getStatus()).isEqualTo(TaskStatus.DONE);
        }

        @Test
        @DisplayName("marks an IN_PROGRESS task as DONE")
        void completeTask_fromInProgress_setsDone() {
            task.setStatus(TaskStatus.IN_PROGRESS);
            given(taskRepository.findByIdAndUserId(10L, 1L)).willReturn(Optional.of(task));
            given(taskRepository.save(any(Task.class))).willReturn(task);

            taskService.completeTask(10L, 1L);

            ArgumentCaptor<Task> captor = ArgumentCaptor.forClass(Task.class);
            then(taskRepository).should().save(captor.capture());
            assertThat(captor.getValue().getStatus()).isEqualTo(TaskStatus.DONE);
        }

        @Test
        @DisplayName("throws BusinessRuleViolationException when task is already DONE")
        void completeTask_whenAlreadyDone_throwsBusinessRuleViolation() {
            task.setStatus(TaskStatus.DONE);
            given(taskRepository.findByIdAndUserId(10L, 1L)).willReturn(Optional.of(task));

            assertThatThrownBy(() -> taskService.completeTask(10L, 1L))
                    .isInstanceOf(BusinessRuleViolationException.class)
                    .hasMessageContaining("DONE");
        }

        @Test
        @DisplayName("throws BusinessRuleViolationException when task is CANCELLED")
        void completeTask_whenCancelled_throwsBusinessRuleViolation() {
            task.setStatus(TaskStatus.CANCELLED);
            given(taskRepository.findByIdAndUserId(10L, 1L)).willReturn(Optional.of(task));

            assertThatThrownBy(() -> taskService.completeTask(10L, 1L))
                    .isInstanceOf(BusinessRuleViolationException.class)
                    .hasMessageContaining("CANCELLED");
        }

        @Test
        @DisplayName("throws ResourceNotFoundException when task not found for completion")
        void completeTask_whenNotFound_throwsNotFound() {
            given(taskRepository.findByIdAndUserId(99L, 1L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> taskService.completeTask(99L, 1L))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    // ── Status transition matrix ──────────────────────────────────────────────

    @Nested
    @DisplayName("Status transition rules")
    class StatusTransitions {

        @Test
        @DisplayName("TODO → CANCELLED is valid")
        void transition_todoCancelled_valid() {
            UpdateTaskRequest request = new UpdateTaskRequest(
                    null, null, TaskStatus.CANCELLED, null, null);
            given(taskRepository.findByIdAndUserId(10L, 1L)).willReturn(Optional.of(task));
            given(taskRepository.save(any())).willReturn(task);

            taskService.updateTask(10L, request, 1L);
        }

        @Test
        @DisplayName("IN_PROGRESS → TODO (unstart) is valid")
        void transition_inProgressTodo_valid() {
            task.setStatus(TaskStatus.IN_PROGRESS);
            UpdateTaskRequest request = new UpdateTaskRequest(
                    null, null, TaskStatus.TODO, null, null);
            given(taskRepository.findByIdAndUserId(10L, 1L)).willReturn(Optional.of(task));
            given(taskRepository.save(any())).willReturn(task);

            taskService.updateTask(10L, request, 1L);
        }

        @Test
        @DisplayName("DONE → TODO (reopen) is valid")
        void transition_doneTodo_valid() {
            task.setStatus(TaskStatus.DONE);
            UpdateTaskRequest request = new UpdateTaskRequest(
                    null, null, TaskStatus.TODO, null, null);
            given(taskRepository.findByIdAndUserId(10L, 1L)).willReturn(Optional.of(task));
            given(taskRepository.save(any())).willReturn(task);

            taskService.updateTask(10L, request, 1L);
        }

        @Test
        @DisplayName("CANCELLED → IN_PROGRESS is invalid")
        void transition_cancelledToInProgress_invalid() {
            task.setStatus(TaskStatus.CANCELLED);
            UpdateTaskRequest request = new UpdateTaskRequest(
                    null, null, TaskStatus.IN_PROGRESS, null, null);
            given(taskRepository.findByIdAndUserId(10L, 1L)).willReturn(Optional.of(task));

            assertThatThrownBy(() -> taskService.updateTask(10L, request, 1L))
                    .isInstanceOf(BusinessRuleViolationException.class);
        }

        @Test
        @DisplayName("DONE → CANCELLED is invalid")
        void transition_doneCancelled_invalid() {
            task.setStatus(TaskStatus.DONE);
            UpdateTaskRequest request = new UpdateTaskRequest(
                    null, null, TaskStatus.CANCELLED, null, null);
            given(taskRepository.findByIdAndUserId(10L, 1L)).willReturn(Optional.of(task));

            assertThatThrownBy(() -> taskService.updateTask(10L, request, 1L))
                    .isInstanceOf(BusinessRuleViolationException.class);
        }
    }

    // ── Ownership security — cross-user isolation ─────────────────────────────

    @Nested
    @DisplayName("Ownership security — cross-user isolation")
    class OwnershipSecurity {

        @Test
        @DisplayName("getTaskById: User A can read their own task")
        void getTaskById_userA_ownTask_succeeds() {
            given(taskRepository.findByIdAndUserId(10L, 1L)).willReturn(Optional.of(task));

            TaskResponse response = taskService.getTaskById(10L, 1L);

            assertThat(response.id()).isEqualTo(10L);
            assertThat(response.userId()).isEqualTo(1L);
        }

        @Test
        @DisplayName("getTaskById: User B cannot read User A's task — throws ResourceNotFoundException")
        void getTaskById_userB_userATask_throwsNotFound() {
            given(taskRepository.findByIdAndUserId(10L, 2L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> taskService.getTaskById(10L, 2L))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Task")
                    .hasMessageContaining("10");

            then(taskRepository).should(never()).findById(any());
        }

        @Test
        @DisplayName("getTaskById: error message does not reveal ownership details")
        void getTaskById_userB_userATask_noOwnershipLeaked() {
            given(taskRepository.findByIdAndUserId(10L, 2L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> taskService.getTaskById(10L, 2L))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageNotContainingAny("alice", "owner", "belongs");
        }

        @Test
        @DisplayName("getUserTasks: query is always scoped to the caller's userId")
        void getUserTasks_alwaysScopedToCallerUserId() {
            Pageable pageable = PageRequest.of(0, 10);
            given(taskRepository.findAllByUserIdWithFilters(
                    eq(1L), any(), any(), any(), any(), any(), any(Pageable.class)))
                    .willReturn(new PageImpl<>(List.of(task)));

            Page<TaskResponse> result = taskService.getUserTasks(1L, emptyFilter(), pageable);

            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).userId()).isEqualTo(1L);
            // Unscoped findAll is never called
            then(taskRepository).should(never()).findAll(any(Pageable.class));
        }

        @Test
        @DisplayName("updateTask: User B cannot update User A's task — save never called")
        void updateTask_userB_userATask_throwsNotFound() {
            UpdateTaskRequest request = new UpdateTaskRequest("Hacked", null, null, null, null);
            given(taskRepository.findByIdAndUserId(10L, 2L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> taskService.updateTask(10L, request, 2L))
                    .isInstanceOf(ResourceNotFoundException.class);

            then(taskRepository).should(never()).save(any());
        }

        @Test
        @DisplayName("deleteTask: User B cannot delete User A's task — deleteById never called")
        void deleteTask_userB_userATask_throwsNotFound() {
            given(taskRepository.existsByIdAndUserId(10L, 2L)).willReturn(false);

            assertThatThrownBy(() -> taskService.deleteTask(10L, 2L))
                    .isInstanceOf(ResourceNotFoundException.class);

            then(taskRepository).should(never()).deleteById(any());
        }

        @Test
        @DisplayName("completeTask: User B cannot complete User A's task — task status unchanged")
        void completeTask_userB_userATask_throwsAndStatusUnchanged() {
            given(taskRepository.findByIdAndUserId(10L, 2L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> taskService.completeTask(10L, 2L))
                    .isInstanceOf(ResourceNotFoundException.class);

            assertThat(task.getStatus()).isEqualTo(TaskStatus.TODO);
            then(taskRepository).should(never()).save(any());
        }
    }
}
