package com.viwe.task_management_system.controller;

import tools.jackson.databind.ObjectMapper;
import com.viwe.task_management_system.config.SecurityConfig;
import com.viwe.task_management_system.dto.request.CreateTaskRequest;
import com.viwe.task_management_system.dto.request.UpdateTaskRequest;
import com.viwe.task_management_system.dto.response.TaskResponse;
import com.viwe.task_management_system.entity.User;
import com.viwe.task_management_system.enums.Role;
import com.viwe.task_management_system.enums.TaskPriority;
import com.viwe.task_management_system.enums.TaskStatus;
import com.viwe.task_management_system.exception.GlobalExceptionHandler;
import com.viwe.task_management_system.exception.ResourceNotFoundException;
import com.viwe.task_management_system.service.TaskService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Security-focused controller slice tests that prove task ownership isolation.
 *
 * <p>These tests answer four explicit questions:
 * <ol>
 *   <li>User A <strong>can</strong> access their own tasks.</li>
 *   <li>User A <strong>cannot</strong> read User B's task by guessing its ID.</li>
 *   <li>User A <strong>cannot</strong> modify User B's task.</li>
 *   <li>User A <strong>cannot</strong> delete User B's task.</li>
 * </ol>
 *
 * <h2>Design</h2>
 * <p>Two users are defined: {@code userA} (id=1) and {@code userB} (id=2).
 * Task 10 belongs to User B. Task 20 belongs to User A.
 *
 * <p>For cross-user tests the security context is populated with User A's
 * principal — exactly what the JWT filter does in production. The service mock
 * is configured to return {@link ResourceNotFoundException} whenever User A's
 * ID (1) is passed with User B's task ID (10), mirroring what
 * {@code TaskServiceImpl.findOwnedTask()} does via {@code findByIdAndUserId}.
 *
 * <p>The tests verify:
 * <ul>
 *   <li>The HTTP status code Spring returns (404 for ownership denial).</li>
 *   <li>The error body shape produced by {@link GlobalExceptionHandler}.</li>
 *   <li>That the service is <strong>never</strong> called with a userId that
 *       was supplied by the client — only with the authenticated principal's ID.
 *       This is proved by strict Mockito argument matchers on every call.</li>
 * </ul>
 *
 * <h2>Why 404 and not 403?</h2>
 * <p>Returning 403 (Forbidden) for a cross-user access attempt leaks the fact
 * that the task exists and belongs to someone else. Returning 404 gives no
 * information about the task's existence or ownership, preventing enumeration
 * attacks. This is the deliberate security choice in {@code TaskServiceImpl}.
 */
@WebMvcTest(controllers = TaskController.class)
@Import({GlobalExceptionHandler.class, SecurityConfig.class,
         com.viwe.task_management_system.security.JwtAuthenticationEntryPoint.class})
@DisplayName("Task Ownership Security")
class TaskOwnershipSecurityTest {

    private static final Long USER_A_ID      = 1L;
    private static final Long USER_B_ID      = 2L;
    private static final Long USER_A_TASK_ID = 20L;   // owned by User A
    private static final Long USER_B_TASK_ID = 10L;   // owned by User B

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private TaskService taskService;

    // ── Required beans for SecurityConfig ────────────────────────────────────
    // The real JwtAuthenticationFilter and JwtAuthenticationEntryPoint are used
    // so the filter chain is authentic. Unauthenticated requests produce real
    // 401s. JwtService and UserDetailsService are mocked (no DB needed).
    @MockitoBean
    private org.springframework.security.core.userdetails.UserDetailsService userDetailsService;

    @MockitoBean
    private com.viwe.task_management_system.service.JwtService jwtService;

    private User userA;
    private User userB;

    private TaskResponse userATask;

    @BeforeEach
    void setUp() {
        userA = User.builder()
                .id(USER_A_ID)
                .firstName("Alice")
                .lastName("Smith")
                .email("alice@example.com")
                .password("hashed")
                .role(Role.USER)
                .build();

        userB = User.builder()
                .id(USER_B_ID)
                .firstName("Bob")
                .lastName("Jones")
                .email("bob@example.com")
                .password("hashed")
                .role(Role.USER)
                .build();

        userATask = new TaskResponse(
                USER_A_TASK_ID, "Alice's task", "Owned by Alice",
                TaskStatus.TODO, TaskPriority.MEDIUM,
                null, USER_A_ID,
                LocalDateTime.now(), LocalDateTime.now());
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    // ── Helper ───────────────────────────────────────────────────────────────

    /**
     * Populates the Spring Security context with the given user as the
     * authenticated principal — mirrors what JwtAuthenticationFilter does.
     */
    private void authenticateAs(User user) {
        var auth = new UsernamePasswordAuthenticationToken(
                user, null, user.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // 1. User A CAN access their own tasks
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("User A can access their own tasks")
    class UserAAccessesOwnTasks {

        @BeforeEach
        void authenticateAsUserA() {
            authenticateAs(userA);
        }

        @Test
        @DisplayName("GET /api/tasks returns 200 with User A's task list")
        void getUserTasks_asUserA_returns200() throws Exception {
            given(taskService.getUserTasks(eq(USER_A_ID), any(), any(), any(Pageable.class)))
                    .willReturn(new PageImpl<>(List.of(userATask)));

            mockMvc.perform(get("/api/tasks"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].id").value(USER_A_TASK_ID))
                    .andExpect(jsonPath("$.content[0].userId").value(USER_A_ID));

            // Service is called with User A's ID — never with a client-supplied ID
            then(taskService).should().getUserTasks(eq(USER_A_ID), any(), any(), any(Pageable.class));
        }

        @Test
        @DisplayName("GET /api/tasks/{id} returns 200 for User A's own task")
        void getTaskById_ownTask_returns200() throws Exception {
            given(taskService.getTaskById(USER_A_TASK_ID, USER_A_ID))
                    .willReturn(userATask);

            mockMvc.perform(get("/api/tasks/{id}", USER_A_TASK_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(USER_A_TASK_ID))
                    .andExpect(jsonPath("$.userId").value(USER_A_ID));

            then(taskService).should().getTaskById(USER_A_TASK_ID, USER_A_ID);
        }

        @Test
        @DisplayName("PUT /api/tasks/{id} returns 200 when User A updates their own task")
        void updateTask_ownTask_returns200() throws Exception {
            UpdateTaskRequest request = new UpdateTaskRequest(
                    "Updated title", null, null, null, null);

            TaskResponse updated = new TaskResponse(
                    USER_A_TASK_ID, "Updated title", "Owned by Alice",
                    TaskStatus.TODO, TaskPriority.MEDIUM, null, USER_A_ID,
                    LocalDateTime.now(), LocalDateTime.now());

            given(taskService.updateTask(eq(USER_A_TASK_ID), any(UpdateTaskRequest.class), eq(USER_A_ID)))
                    .willReturn(updated);

            mockMvc.perform(put("/api/tasks/{id}", USER_A_TASK_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.title").value("Updated title"))
                    .andExpect(jsonPath("$.userId").value(USER_A_ID));

            then(taskService).should()
                    .updateTask(eq(USER_A_TASK_ID), any(UpdateTaskRequest.class), eq(USER_A_ID));
        }

        @Test
        @DisplayName("DELETE /api/tasks/{id} returns 204 when User A deletes their own task")
        void deleteTask_ownTask_returns204() throws Exception {
            mockMvc.perform(delete("/api/tasks/{id}", USER_A_TASK_ID))
                    .andExpect(status().isNoContent());

            then(taskService).should().deleteTask(USER_A_TASK_ID, USER_A_ID);
        }

        @Test
        @DisplayName("PATCH /api/tasks/{id}/complete returns 200 when User A completes their own task")
        void completeTask_ownTask_returns200() throws Exception {
            TaskResponse completed = new TaskResponse(
                    USER_A_TASK_ID, "Alice's task", "Owned by Alice",
                    TaskStatus.DONE, TaskPriority.MEDIUM, null, USER_A_ID,
                    LocalDateTime.now(), LocalDateTime.now());

            given(taskService.completeTask(USER_A_TASK_ID, USER_A_ID)).willReturn(completed);

            mockMvc.perform(patch("/api/tasks/{id}/complete", USER_A_TASK_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("DONE"))
                    .andExpect(jsonPath("$.userId").value(USER_A_ID));

            then(taskService).should().completeTask(USER_A_TASK_ID, USER_A_ID);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // 2. User A CANNOT read User B's task by guessing its ID
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("User A cannot read User B's task")
    class UserACannotReadUserBTask {

        @BeforeEach
        void authenticateAsUserA() {
            authenticateAs(userA);
        }

        @Test
        @DisplayName("GET /api/tasks/{id} returns 404 when User A guesses User B's task ID")
        void getTaskById_userBsTask_returns404() throws Exception {
            // Service receives User A's ID (from security context) + User B's task ID.
            // findByIdAndUserId(10, 1) returns empty → ResourceNotFoundException.
            given(taskService.getTaskById(USER_B_TASK_ID, USER_A_ID))
                    .willThrow(new ResourceNotFoundException("Task", USER_B_TASK_ID));

            mockMvc.perform(get("/api/tasks/{id}", USER_B_TASK_ID))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.status").value(404))
                    .andExpect(jsonPath("$.error").value("RESOURCE_NOT_FOUND"));

            // Critically: service was called with USER_A_ID — not USER_B_ID.
            // The controller never accepts a userId from the client.
            then(taskService).should().getTaskById(USER_B_TASK_ID, USER_A_ID);
            then(taskService).should(never()).getTaskById(USER_B_TASK_ID, USER_B_ID);
        }

        @Test
        @DisplayName("GET /api/tasks does not include User B's tasks in User A's list")
        void getUserTasks_returnsOnlyUserATasks_notUserBTasks() throws Exception {
            // Service is always called with User A's ID; User B's tasks never appear.
            given(taskService.getUserTasks(eq(USER_A_ID), any(), any(), any(Pageable.class)))
                    .willReturn(new PageImpl<>(List.of(userATask)));

            mockMvc.perform(get("/api/tasks"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content.length()").value(1))
                    .andExpect(jsonPath("$.content[0].userId").value(USER_A_ID));

            then(taskService).should().getUserTasks(eq(USER_A_ID), any(), any(), any(Pageable.class));
            then(taskService).should(never())
                    .getUserTasks(eq(USER_B_ID), any(), any(), any(Pageable.class));
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // 3. User A CANNOT modify User B's task
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("User A cannot modify User B's task")
    class UserACannotModifyUserBTask {

        @BeforeEach
        void authenticateAsUserA() {
            authenticateAs(userA);
        }

        @Test
        @DisplayName("PUT /api/tasks/{id} returns 404 when User A attempts to update User B's task")
        void updateTask_userBsTask_returns404() throws Exception {
            UpdateTaskRequest request = new UpdateTaskRequest(
                    "Hacked title", null, null, null, null);

            // Service sees User A's ID paired with User B's task ID — ownership check fails.
            given(taskService.updateTask(eq(USER_B_TASK_ID), any(UpdateTaskRequest.class), eq(USER_A_ID)))
                    .willThrow(new ResourceNotFoundException("Task", USER_B_TASK_ID));

            mockMvc.perform(put("/api/tasks/{id}", USER_B_TASK_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.status").value(404))
                    .andExpect(jsonPath("$.error").value("RESOURCE_NOT_FOUND"));

            // Controller passed USER_A_ID (from principal), never USER_B_ID.
            then(taskService).should()
                    .updateTask(eq(USER_B_TASK_ID), any(UpdateTaskRequest.class), eq(USER_A_ID));
            then(taskService).should(never())
                    .updateTask(eq(USER_B_TASK_ID), any(), eq(USER_B_ID));
        }

        @Test
        @DisplayName("PATCH /api/tasks/{id}/complete returns 404 when User A tries to complete User B's task")
        void completeTask_userBsTask_returns404() throws Exception {
            given(taskService.completeTask(USER_B_TASK_ID, USER_A_ID))
                    .willThrow(new ResourceNotFoundException("Task", USER_B_TASK_ID));

            mockMvc.perform(patch("/api/tasks/{id}/complete", USER_B_TASK_ID))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.status").value(404))
                    .andExpect(jsonPath("$.error").value("RESOURCE_NOT_FOUND"));

            then(taskService).should().completeTask(USER_B_TASK_ID, USER_A_ID);
            then(taskService).should(never()).completeTask(USER_B_TASK_ID, USER_B_ID);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // 4. User A CANNOT delete User B's task
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("User A cannot delete User B's task")
    class UserACannotDeleteUserBTask {

        @BeforeEach
        void authenticateAsUserA() {
            authenticateAs(userA);
        }

        @Test
        @DisplayName("DELETE /api/tasks/{id} returns 404 when User A attempts to delete User B's task")
        void deleteTask_userBsTask_returns404() throws Exception {
            willThrow(new ResourceNotFoundException("Task", USER_B_TASK_ID))
                    .given(taskService).deleteTask(USER_B_TASK_ID, USER_A_ID);

            mockMvc.perform(delete("/api/tasks/{id}", USER_B_TASK_ID))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.status").value(404))
                    .andExpect(jsonPath("$.error").value("RESOURCE_NOT_FOUND"));

            // deleteTask was invoked with USER_A_ID — never USER_B_ID.
            // This proves the controller is not accepting a userId from the URL.
            then(taskService).should().deleteTask(USER_B_TASK_ID, USER_A_ID);
            then(taskService).should(never()).deleteTask(USER_B_TASK_ID, USER_B_ID);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // 5. Unauthenticated requests are rejected before reaching the controller
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Unauthenticated requests are rejected")
    class UnauthenticatedRequests {

        // No @BeforeEach — security context is deliberately empty for this suite.

        @Test
        @DisplayName("GET /api/tasks without a token returns 401")
        void getTasks_noToken_returns401() throws Exception {
            mockMvc.perform(get("/api/tasks"))
                    .andExpect(status().isUnauthorized());

            then(taskService).should(never())
                    .getUserTasks(any(), any(), any(), any());
        }

        @Test
        @DisplayName("GET /api/tasks/{id} without a token returns 401")
        void getTaskById_noToken_returns401() throws Exception {
            mockMvc.perform(get("/api/tasks/{id}", USER_B_TASK_ID))
                    .andExpect(status().isUnauthorized());

            then(taskService).should(never()).getTaskById(any(), any());
        }

        @Test
        @DisplayName("POST /api/tasks without a token returns 401")
        void createTask_noToken_returns401() throws Exception {
            CreateTaskRequest request = new CreateTaskRequest(
                    "Sneaky task", null, null, null, null);

            mockMvc.perform(post("/api/tasks")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isUnauthorized());

            then(taskService).should(never()).createTask(any(), any());
        }

        @Test
        @DisplayName("PUT /api/tasks/{id} without a token returns 401")
        void updateTask_noToken_returns401() throws Exception {
            UpdateTaskRequest request = new UpdateTaskRequest(
                    "Hacked title", null, null, null, null);

            mockMvc.perform(put("/api/tasks/{id}", USER_B_TASK_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isUnauthorized());

            then(taskService).should(never()).updateTask(any(), any(), any());
        }

        @Test
        @DisplayName("DELETE /api/tasks/{id} without a token returns 401")
        void deleteTask_noToken_returns401() throws Exception {
            mockMvc.perform(delete("/api/tasks/{id}", USER_B_TASK_ID))
                    .andExpect(status().isUnauthorized());

            then(taskService).should(never()).deleteTask(any(), any());
        }

        @Test
        @DisplayName("PATCH /api/tasks/{id}/complete without a token returns 401")
        void completeTask_noToken_returns401() throws Exception {
            mockMvc.perform(patch("/api/tasks/{id}/complete", USER_B_TASK_ID))
                    .andExpect(status().isUnauthorized());

            then(taskService).should(never()).completeTask(any(), any());
        }
    }
}
