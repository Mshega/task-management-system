package com.viwe.task_management_system.service;

import com.viwe.task_management_system.dto.request.CreateTaskRequest;
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
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

/**
 * Pure unit tests for {@link TaskServiceImpl}.
 *
 * <p>No Spring context is loaded. All dependencies are mocked with Mockito.
 * Tests focus on business logic: ownership enforcement, not-found scenarios,
 * status transition validation, and correct delegation to the repository.
 */
@ExtendWith(MockitoExtension.class)
class TaskServiceTest {

    @Mock
    private TaskRepository taskRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private TaskServiceImpl taskService;

    private User owner;
    private User otherUser;
    private Task task;

    @BeforeEach
    void setUp() {
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

    // ── getUserTasks ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("getUserTasks")
    class GetUserTasks {

        @Test
        @DisplayName("returns all user tasks when no filters supplied")
        void getUserTasks_noFilter_returnsAllUserTasks() {
            Pageable pageable = PageRequest.of(0, 10);
            Page<Task> page = new PageImpl<>(List.of(task));

            given(taskRepository.findAllByUserId(1L, pageable)).willReturn(page);

            Page<TaskResponse> result = taskService.getUserTasks(1L, null, null, pageable);

            assertThat(result.getTotalElements()).isEqualTo(1);
            assertThat(result.getContent().get(0).id()).isEqualTo(10L);
        }

        @Test
        @DisplayName("filters by status when status is supplied")
        void getUserTasks_withStatusFilter_delegatesToStatusRepository() {
            Pageable pageable = PageRequest.of(0, 10);
            given(taskRepository.findAllByUserIdAndStatus(1L, TaskStatus.TODO, pageable))
                    .willReturn(new PageImpl<>(List.of(task)));

            taskService.getUserTasks(1L, TaskStatus.TODO, null, pageable);

            then(taskRepository).should().findAllByUserIdAndStatus(1L, TaskStatus.TODO, pageable);
            then(taskRepository).should(never()).findAllByUserId(any(), any());
        }

        @Test
        @DisplayName("filters by priority when priority is supplied")
        void getUserTasks_withPriorityFilter_delegatesToPriorityRepository() {
            Pageable pageable = PageRequest.of(0, 10);
            given(taskRepository.findAllByUserIdAndPriority(1L, TaskPriority.HIGH, pageable))
                    .willReturn(new PageImpl<>(List.of(task)));

            taskService.getUserTasks(1L, null, TaskPriority.HIGH, pageable);

            then(taskRepository).should().findAllByUserIdAndPriority(1L, TaskPriority.HIGH, pageable);
            then(taskRepository).should(never()).findAllByUserId(any(), any());
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
            // otherUser queries for owner's task — repository returns empty
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
            // Description unchanged because request.description() was null
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
            // task is already TODO, request sets TODO again
            UpdateTaskRequest request = new UpdateTaskRequest(
                    null, null, TaskStatus.TODO, null, null);

            given(taskRepository.findByIdAndUserId(10L, 1L)).willReturn(Optional.of(task));
            given(taskRepository.save(any(Task.class))).willReturn(task);

            taskService.updateTask(10L, request, 1L);  // must not throw

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

            taskService.updateTask(10L, request, 1L); // must not throw
        }

        @Test
        @DisplayName("IN_PROGRESS → TODO (unstart) is valid")
        void transition_inProgressTodo_valid() {
            task.setStatus(TaskStatus.IN_PROGRESS);
            UpdateTaskRequest request = new UpdateTaskRequest(
                    null, null, TaskStatus.TODO, null, null);
            given(taskRepository.findByIdAndUserId(10L, 1L)).willReturn(Optional.of(task));
            given(taskRepository.save(any())).willReturn(task);

            taskService.updateTask(10L, request, 1L); // must not throw
        }

        @Test
        @DisplayName("DONE → TODO (reopen) is valid")
        void transition_doneTodo_valid() {
            task.setStatus(TaskStatus.DONE);
            UpdateTaskRequest request = new UpdateTaskRequest(
                    null, null, TaskStatus.TODO, null, null);
            given(taskRepository.findByIdAndUserId(10L, 1L)).willReturn(Optional.of(task));
            given(taskRepository.save(any())).willReturn(task);

            taskService.updateTask(10L, request, 1L); // must not throw
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
}
