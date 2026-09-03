package com.viwe.task_management_system.controller;

import tools.jackson.databind.ObjectMapper;
import com.viwe.task_management_system.config.SecurityConfig;
import com.viwe.task_management_system.dto.request.CreateTaskRequest;
import com.viwe.task_management_system.dto.request.TaskFilterRequest;
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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willDoNothing;
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
 * Slice tests for {@link TaskController}.
 *
 * <p>Uses {@code @WebMvcTest} — only the web layer is loaded. The
 * {@link TaskService} is mocked with {@code @MockitoBean}.
 *
 * <p>The authenticated user is injected directly into the Spring Security
 * context before each test using a real {@link User} entity as the principal,
 * mirroring what the JWT filter does in production.
 *
 * <p>Tests are organised into nested classes by concern:
 * <ul>
 *   <li>{@code ListEndpoint} — pagination, filtering, sorting, and ownership
 *       of the GET /api/tasks endpoint.</li>
 *   <li>Other endpoint classes — CRUD operations.</li>
 * </ul>
 */
@WebMvcTest(controllers = TaskController.class)
@Import({GlobalExceptionHandler.class, SecurityConfig.class,
         com.viwe.task_management_system.security.JwtAuthenticationEntryPoint.class})
class TaskControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private TaskService taskService;

    @MockitoBean
    private org.springframework.security.core.userdetails.UserDetailsService userDetailsService;

    @MockitoBean
    private com.viwe.task_management_system.service.JwtService jwtService;

    private User authenticatedUser;
    private TaskResponse sampleTask;

    @BeforeEach
    void setUp() {
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

    // ═══════════════════════════════════════════════════════════════════════
    // GET /api/tasks — filtering, sorting, pagination
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("GET /api/tasks — filtering, sorting, pagination")
    class ListEndpoint {

        // ── Baseline ──────────────────────────────────────────────────────

        @Test
        @DisplayName("no params returns 200 with page of tasks")
        void noParams_returns200WithPage() throws Exception {
            given(taskService.getUserTasks(eq(1L), any(TaskFilterRequest.class), any(Pageable.class)))
                    .willReturn(new PageImpl<>(List.of(sampleTask)));

            mockMvc.perform(get("/api/tasks"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].id").value(10))
                    .andExpect(jsonPath("$.content[0].title").value("Write tests"))
                    .andExpect(jsonPath("$.content[0].userId").value(1));
        }

        @Test
        @DisplayName("response includes Spring Data page metadata (totalElements, totalPages)")
        void response_includesPageMetadata() throws Exception {
            given(taskService.getUserTasks(eq(1L), any(TaskFilterRequest.class), any(Pageable.class)))
                    .willReturn(new PageImpl<>(List.of(sampleTask)));

            mockMvc.perform(get("/api/tasks"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").value(1))
                    .andExpect(jsonPath("$.totalPages").value(1))
                    .andExpect(jsonPath("$.size").exists())
                    .andExpect(jsonPath("$.number").exists());
        }

        // ── Status filter ──────────────────────────────────────────────────

        @Test
        @DisplayName("?status=TODO builds filter with status=TODO and passes to service")
        void statusParam_buildsCorrectFilter() throws Exception {
            given(taskService.getUserTasks(
                    eq(1L),
                    argThat(f -> f.status() == TaskStatus.TODO && f.priority() == null
                                 && f.title() == null),
                    any(Pageable.class)))
                    .willReturn(new PageImpl<>(List.of(sampleTask)));

            mockMvc.perform(get("/api/tasks").param("status", "TODO"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isArray());
        }

        @Test
        @DisplayName("invalid status value returns 400 MALFORMED_REQUEST")
        void invalidStatus_returns400() throws Exception {
            mockMvc.perform(get("/api/tasks").param("status", "UNKNOWN_STATUS"))
                    .andExpect(status().isBadRequest());
        }

        // ── Priority filter ────────────────────────────────────────────────

        @Test
        @DisplayName("?priority=HIGH builds filter with priority=HIGH")
        void priorityParam_buildsCorrectFilter() throws Exception {
            given(taskService.getUserTasks(
                    eq(1L),
                    argThat(f -> f.priority() == TaskPriority.HIGH && f.status() == null),
                    any(Pageable.class)))
                    .willReturn(new PageImpl<>(List.of(sampleTask)));

            mockMvc.perform(get("/api/tasks").param("priority", "HIGH"))
                    .andExpect(status().isOk());
        }

        // ── Title search ───────────────────────────────────────────────────

        @Test
        @DisplayName("?title=bug passes title to service filter")
        void titleParam_passedToServiceFilter() throws Exception {
            given(taskService.getUserTasks(
                    eq(1L),
                    argThat(f -> "bug".equals(f.title())),
                    any(Pageable.class)))
                    .willReturn(new PageImpl<>(List.of(sampleTask)));

            mockMvc.perform(get("/api/tasks").param("title", "bug"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isArray());
        }

        @Test
        @DisplayName("?title= (blank) is treated the same as omitting title — service receives null")
        void blankTitle_normalisedToNull() throws Exception {
            // TaskFilterRequest normalises blank title to null
            given(taskService.getUserTasks(
                    eq(1L),
                    argThat(f -> f.title() == null),
                    any(Pageable.class)))
                    .willReturn(new PageImpl<>(List.of(sampleTask)));

            mockMvc.perform(get("/api/tasks").param("title", "   "))
                    .andExpect(status().isOk());
        }

        // ── Due-date filters ───────────────────────────────────────────────

        @Test
        @DisplayName("?dueOnOrBefore=2030-01-01 is parsed as ISO date and forwarded to service")
        void dueOnOrBeforeParam_parsedAndForwarded() throws Exception {
            LocalDate bound = LocalDate.of(2030, 1, 1);
            given(taskService.getUserTasks(
                    eq(1L),
                    argThat(f -> bound.equals(f.dueOnOrBefore())),
                    any(Pageable.class)))
                    .willReturn(new PageImpl<>(List.of(sampleTask)));

            mockMvc.perform(get("/api/tasks").param("dueOnOrBefore", "2030-01-01"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("?dueOnOrAfter=2020-01-01 is parsed as ISO date and forwarded to service")
        void dueOnOrAfterParam_parsedAndForwarded() throws Exception {
            LocalDate bound = LocalDate.of(2020, 1, 1);
            given(taskService.getUserTasks(
                    eq(1L),
                    argThat(f -> bound.equals(f.dueOnOrAfter())),
                    any(Pageable.class)))
                    .willReturn(new PageImpl<>(List.of(sampleTask)));

            mockMvc.perform(get("/api/tasks").param("dueOnOrAfter", "2020-01-01"))
                    .andExpect(status().isOk());
        }

        // ── Combined filters ───────────────────────────────────────────────

        @Test
        @DisplayName("status + priority + title combined are all present in the filter object")
        void combinedParams_allReachService() throws Exception {
            given(taskService.getUserTasks(
                    eq(1L),
                    argThat(f -> f.status() == TaskStatus.TODO
                                 && f.priority() == TaskPriority.HIGH
                                 && "bug".equals(f.title())),
                    any(Pageable.class)))
                    .willReturn(new PageImpl<>(List.of(sampleTask)));

            mockMvc.perform(get("/api/tasks")
                            .param("status", "TODO")
                            .param("priority", "HIGH")
                            .param("title", "bug"))
                    .andExpect(status().isOk());
        }

        // ── Pagination ─────────────────────────────────────────────────────

        @Test
        @DisplayName("?page=0&size=5 produces pageable with those values")
        void paginationParams_forwardedToService() throws Exception {
            given(taskService.getUserTasks(
                    eq(1L),
                    any(TaskFilterRequest.class),
                    argThat(p -> p.getPageNumber() == 0 && p.getPageSize() == 5)))
                    .willReturn(new PageImpl<>(List.of(sampleTask)));

            mockMvc.perform(get("/api/tasks")
                            .param("page", "0")
                            .param("size", "5"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("?page=2&size=3 produces pageable for the correct page")
        void paginationParams_secondPage_forwardedCorrectly() throws Exception {
            given(taskService.getUserTasks(
                    eq(1L),
                    any(TaskFilterRequest.class),
                    argThat(p -> p.getPageNumber() == 2 && p.getPageSize() == 3)))
                    .willReturn(new PageImpl<>(List.of()));

            mockMvc.perform(get("/api/tasks")
                            .param("page", "2")
                            .param("size", "3"))
                    .andExpect(status().isOk());
        }

        // ── Sorting ────────────────────────────────────────────────────────

        @Test
        @DisplayName("?sort=dueDate,asc produces ascending dueDate sort order")
        void sortByDueDateAsc_producesCorrectSortOrder() throws Exception {
            given(taskService.getUserTasks(
                    eq(1L),
                    any(TaskFilterRequest.class),
                    argThat(p -> p.getSort().getOrderFor("dueDate") != null
                                 && p.getSort().getOrderFor("dueDate").isAscending())))
                    .willReturn(new PageImpl<>(List.of(sampleTask)));

            mockMvc.perform(get("/api/tasks").param("sort", "dueDate,asc"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("?sort=createdAt,desc produces descending createdAt sort")
        void sortByCreatedAtDesc_producesCorrectSortOrder() throws Exception {
            given(taskService.getUserTasks(
                    eq(1L),
                    any(TaskFilterRequest.class),
                    argThat(p -> p.getSort().getOrderFor("createdAt") != null
                                 && p.getSort().getOrderFor("createdAt").isDescending())))
                    .willReturn(new PageImpl<>(List.of(sampleTask)));

            mockMvc.perform(get("/api/tasks").param("sort", "createdAt,desc"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("default sort is createdAt DESC when no sort param is provided")
        void defaultSort_isCreatedAtDesc() throws Exception {
            given(taskService.getUserTasks(
                    eq(1L),
                    any(TaskFilterRequest.class),
                    argThat(p -> p.getSort().getOrderFor("createdAt") != null
                                 && p.getSort().getOrderFor("createdAt").isDescending())))
                    .willReturn(new PageImpl<>(List.of(sampleTask)));

            mockMvc.perform(get("/api/tasks"))
                    .andExpect(status().isOk());
        }

        // ── Ownership ──────────────────────────────────────────────────────

        @Test
        @DisplayName("userId always comes from the authenticated principal — never from a query param")
        void userId_comesFromPrincipal_neverFromQueryParam() throws Exception {
            given(taskService.getUserTasks(eq(1L), any(TaskFilterRequest.class), any(Pageable.class)))
                    .willReturn(new PageImpl<>(List.of(sampleTask)));

            // Attempt to inject a different userId via query param — must be ignored
            mockMvc.perform(get("/api/tasks").param("userId", "99"))
                    .andExpect(status().isOk());

            // Service must have been called with the principal's ID (1L), not 99L
            then(taskService).should().getUserTasks(eq(1L), any(), any());
            then(taskService).should(never()).getUserTasks(eq(99L), any(), any());
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // GET /api/tasks/{id}
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("GET /api/tasks/{id}")
    class GetById {

        @Test
        @DisplayName("returns 200 with task")
        void returnsTask() throws Exception {
            given(taskService.getTaskById(10L, 1L)).willReturn(sampleTask);

            mockMvc.perform(get("/api/tasks/10"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(10))
                    .andExpect(jsonPath("$.title").value("Write tests"))
                    .andExpect(jsonPath("$.status").value("TODO"));
        }

        @Test
        @DisplayName("returns 404 when task not found")
        void whenNotFound_returns404() throws Exception {
            given(taskService.getTaskById(99L, 1L))
                    .willThrow(new ResourceNotFoundException("Task", 99L));

            mockMvc.perform(get("/api/tasks/99"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error").value("RESOURCE_NOT_FOUND"))
                    .andExpect(jsonPath("$.status").value(404));
        }

        @Test
        @DisplayName("returns 404 for another user's task (ownership hidden)")
        void anotherUserTask_returns404() throws Exception {
            given(taskService.getTaskById(20L, 1L))
                    .willThrow(new ResourceNotFoundException("Task", 20L));

            mockMvc.perform(get("/api/tasks/20"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error").value("RESOURCE_NOT_FOUND"));
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // POST /api/tasks
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("POST /api/tasks")
    class CreateTask {

        @Test
        @DisplayName("returns 201 with created task")
        void validRequest_returns201() throws Exception {
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
        @DisplayName("missing title returns 400 VALIDATION_FAILED")
        void missingTitle_returns400() throws Exception {
            mockMvc.perform(post("/api/tasks")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"title\": \"\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("VALIDATION_FAILED"))
                    .andExpect(jsonPath("$.fieldErrors").isArray());
        }

        @Test
        @DisplayName("malformed body returns 400 MALFORMED_REQUEST")
        void malformedBody_returns400() throws Exception {
            mockMvc.perform(post("/api/tasks")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{bad json"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("MALFORMED_REQUEST"));
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // PUT /api/tasks/{id}
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("PUT /api/tasks/{id}")
    class UpdateTask {

        @Test
        @DisplayName("returns 200 with updated task")
        void validRequest_returns200() throws Exception {
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
        @DisplayName("invalid transition returns 400 BUSINESS_RULE_VIOLATION")
        void invalidTransition_returns400() throws Exception {
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
        @DisplayName("not found returns 404")
        void notFound_returns404() throws Exception {
            UpdateTaskRequest request = new UpdateTaskRequest(
                    "title", null, null, null, null);

            given(taskService.updateTask(eq(99L), any(), eq(1L)))
                    .willThrow(new ResourceNotFoundException("Task", 99L));

            mockMvc.perform(put("/api/tasks/99")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isNotFound());
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // DELETE /api/tasks/{id}
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("DELETE /api/tasks/{id}")
    class DeleteTask {

        @Test
        @DisplayName("returns 204 No Content")
        void owned_returns204() throws Exception {
            willDoNothing().given(taskService).deleteTask(10L, 1L);

            mockMvc.perform(delete("/api/tasks/10"))
                    .andExpect(status().isNoContent());
        }

        @Test
        @DisplayName("not found returns 404")
        void notFound_returns404() throws Exception {
            willThrow(new ResourceNotFoundException("Task", 99L))
                    .given(taskService).deleteTask(99L, 1L);

            mockMvc.perform(delete("/api/tasks/99"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error").value("RESOURCE_NOT_FOUND"));
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // PATCH /api/tasks/{id}/complete
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("PATCH /api/tasks/{id}/complete")
    class CompleteTask {

        @Test
        @DisplayName("returns 200 with DONE status")
        void valid_returns200WithDoneStatus() throws Exception {
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
        @DisplayName("already DONE returns 400 BUSINESS_RULE_VIOLATION")
        void alreadyDone_returns400() throws Exception {
            given(taskService.completeTask(10L, 1L))
                    .willThrow(new BusinessRuleViolationException(
                            "Cannot transition task from DONE to DONE"));

            mockMvc.perform(patch("/api/tasks/10/complete"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("BUSINESS_RULE_VIOLATION"));
        }
    }
}
