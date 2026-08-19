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
import com.viwe.task_management_system.exception.BusinessRuleViolationException;
import com.viwe.task_management_system.exception.GlobalExceptionHandler;
import com.viwe.task_management_system.exception.ResourceNotFoundException;
import com.viwe.task_management_system.service.TaskService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Slice tests for {@link TaskController}.
 *
 * <p>Uses {@code @WebMvcTest} — only the web layer is loaded. The
 * {@link TaskService} is mocked with {@code @MockitoBean} (Spring Boot 4
 * replacement for the removed {@code @MockBean}).
 *
 * <p>The authenticated user is injected directly into the Spring Security
 * context before each test using a real {@link User} entity as the principal.
 * This mirrors exactly what the JWT filter will do in production and tests
 * the {@code @AuthenticationPrincipal} binding in the controller.
 */
@WebMvcTest(controllers = TaskController.class)
@Import({GlobalExceptionHandler.class, SecurityConfig.class})
class TaskControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private TaskService taskService;

    private User authenticatedUser;
    private TaskResponse sampleTask;

    @BeforeEach
    void setUp() {
        // Build a real User entity and place it in the security context as the principal.
        // This is exactly what the JWT authentication filter will do — the controller
        // code is identical in both test and production scenarios.
        authenticatedUser = User.builder()
                .id(1L)
                .firstName("Alice")
                .lastName("Smith")
                .email("alice@example.com")
                .password("hashed")
                .role(Role.USER)
                .build();

        var auth = new UsernamePasswordAuthenticationToken(
                authenticatedUser, null, authenticatedUser.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);

        sampleTask = new TaskResponse(
                10L, "Write tests", "Cover controller layer",
                TaskStatus.TODO, TaskPriority.MEDIUM,
                LocalDate.now().plusDays(7), 1L,
                LocalDateTime.now(), LocalDateTime.now());
    }

    // ── GET /api/tasks ────────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /api/tasks returns 200 with page of tasks")
    void getTasks_returnsPageOfTasks() throws Exception {
        given(taskService.getUserTasks(eq(1L), any(), any(), any(Pageable.class)))
                .willReturn(new PageImpl<>(List.of(sampleTask)));

        mockMvc.perform(get("/api/tasks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(10))
                .andExpect(jsonPath("$.content[0].title").value("Write tests"))
                .andExpect(jsonPath("$.content[0].userId").value(1));
    }

    @Test
    @DisplayName("GET /api/tasks with status filter passes filter to service")
    void getTasks_withStatusFilter_passesFilterToService() throws Exception {
        given(taskService.getUserTasks(eq(1L), eq(TaskStatus.TODO), any(), any(Pageable.class)))
                .willReturn(new PageImpl<>(List.of(sampleTask)));

        mockMvc.perform(get("/api/tasks").param("status", "TODO"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    // ── GET /api/tasks/{id} ───────────────────────────────────────────────────

    @Test
    @DisplayName("GET /api/tasks/{id} returns 200 with task")
    void getTaskById_returnsTask() throws Exception {
        given(taskService.getTaskById(10L, 1L)).willReturn(sampleTask);

        mockMvc.perform(get("/api/tasks/10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.title").value("Write tests"))
                .andExpect(jsonPath("$.status").value("TODO"));
    }

    @Test
    @DisplayName("GET /api/tasks/{id} returns 404 when task not found")
    void getTaskById_whenNotFound_returns404() throws Exception {
        given(taskService.getTaskById(99L, 1L))
                .willThrow(new ResourceNotFoundException("Task", 99L));

        mockMvc.perform(get("/api/tasks/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("RESOURCE_NOT_FOUND"))
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    @DisplayName("GET /api/tasks/{id} for another user's task returns 404 (ownership hidden)")
    void getTaskById_whenOwnedByAnotherUser_returns404() throws Exception {
        // Service returns not-found for cross-user access (ownership is hidden)
        given(taskService.getTaskById(20L, 1L))
                .willThrow(new ResourceNotFoundException("Task", 20L));

        mockMvc.perform(get("/api/tasks/20"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("RESOURCE_NOT_FOUND"));
    }

    // ── POST /api/tasks ───────────────────────────────────────────────────────

    @Test
    @DisplayName("POST /api/tasks returns 201 with created task")
    void createTask_withValidRequest_returns201() throws Exception {
        CreateTaskRequest request = new CreateTaskRequest(
                "New task", "Details", null, null, null);

        given(taskService.createTask(any(CreateTaskRequest.class), eq(1L)))
                .willReturn(sampleTask);

        mockMvc.perform(post("/api/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.userId").value(1));
    }

    @Test
    @DisplayName("POST /api/tasks with missing title returns 400 VALIDATION_FAILED")
    void createTask_withMissingTitle_returns400() throws Exception {
        // Empty title violates @NotBlank
        String body = "{\"title\": \"\"}";

        mockMvc.perform(post("/api/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors").isArray());
    }

    @Test
    @DisplayName("POST /api/tasks with missing body returns 400 MALFORMED_REQUEST")
    void createTask_withMalformedBody_returns400() throws Exception {
        mockMvc.perform(post("/api/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{bad json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("MALFORMED_REQUEST"));
    }

    // ── PUT /api/tasks/{id} ───────────────────────────────────────────────────

    @Test
    @DisplayName("PUT /api/tasks/{id} returns 200 with updated task")
    void updateTask_withValidRequest_returns200() throws Exception {
        UpdateTaskRequest request = new UpdateTaskRequest(
                "Updated title", null, null, null, null);

        TaskResponse updated = new TaskResponse(
                10L, "Updated title", "Cover controller layer",
                TaskStatus.TODO, TaskPriority.MEDIUM, null, 1L,
                LocalDateTime.now(), LocalDateTime.now());

        given(taskService.updateTask(eq(10L), any(UpdateTaskRequest.class), eq(1L)))
                .willReturn(updated);

        mockMvc.perform(put("/api/tasks/10")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Updated title"));
    }

    @Test
    @DisplayName("PUT /api/tasks/{id} with invalid transition returns 400 BUSINESS_RULE_VIOLATION")
    void updateTask_withInvalidTransition_returns400() throws Exception {
        UpdateTaskRequest request = new UpdateTaskRequest(
                null, null, TaskStatus.DONE, null, null);

        given(taskService.updateTask(eq(10L), any(UpdateTaskRequest.class), eq(1L)))
                .willThrow(new BusinessRuleViolationException(
                        "Cannot transition task from TODO to DONE"));

        mockMvc.perform(put("/api/tasks/10")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("BUSINESS_RULE_VIOLATION"))
                .andExpect(jsonPath("$.message").value("Cannot transition task from TODO to DONE"));
    }

    @Test
    @DisplayName("PUT /api/tasks/{id} when not found returns 404")
    void updateTask_whenNotFound_returns404() throws Exception {
        UpdateTaskRequest request = new UpdateTaskRequest(
                "title", null, null, null, null);

        given(taskService.updateTask(eq(99L), any(), eq(1L)))
                .willThrow(new ResourceNotFoundException("Task", 99L));

        mockMvc.perform(put("/api/tasks/99")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
    }

    // ── DELETE /api/tasks/{id} ────────────────────────────────────────────────

    @Test
    @DisplayName("DELETE /api/tasks/{id} returns 204 No Content")
    void deleteTask_whenOwned_returns204() throws Exception {
        willDoNothing().given(taskService).deleteTask(10L, 1L);

        mockMvc.perform(delete("/api/tasks/10"))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("DELETE /api/tasks/{id} when not found returns 404")
    void deleteTask_whenNotFound_returns404() throws Exception {
        willThrow(new ResourceNotFoundException("Task", 99L))
                .given(taskService).deleteTask(99L, 1L);

        mockMvc.perform(delete("/api/tasks/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("RESOURCE_NOT_FOUND"));
    }

    // ── PATCH /api/tasks/{id}/complete ────────────────────────────────────────

    @Test
    @DisplayName("PATCH /api/tasks/{id}/complete returns 200 with DONE status")
    void completeTask_whenValid_returns200WithDoneStatus() throws Exception {
        TaskResponse completed = new TaskResponse(
                10L, "Write tests", null,
                TaskStatus.DONE, TaskPriority.MEDIUM, null, 1L,
                LocalDateTime.now(), LocalDateTime.now());

        given(taskService.completeTask(10L, 1L)).willReturn(completed);

        mockMvc.perform(patch("/api/tasks/10/complete"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DONE"));
    }

    @Test
    @DisplayName("PATCH /api/tasks/{id}/complete when already DONE returns 400")
    void completeTask_whenAlreadyDone_returns400() throws Exception {
        given(taskService.completeTask(10L, 1L))
                .willThrow(new BusinessRuleViolationException(
                        "Cannot transition task from DONE to DONE"));

        mockMvc.perform(patch("/api/tasks/10/complete"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("BUSINESS_RULE_VIOLATION"));
    }
}
