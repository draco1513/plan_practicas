package com.esfx.taskflow.projects;

import com.esfx.taskflow.common.ProjectsApi.TaskView;
import com.esfx.taskflow.common.UserSummary;
import jakarta.validation.constraints.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Objetos de entrada y salida de la API del módulo projects. */
public final class ProjectsDtos {
    private ProjectsDtos() {}

    public static final String PROJECT_STATUS = "PLANNED|ACTIVE|ON_HOLD|COMPLETED|CANCELLED";
    public static final String TASK_STATUS = "TODO|IN_PROGRESS|DONE";
    public static final String PRIORITY = "LOW|MEDIUM|HIGH";

    public record ProjectDto(long id, String code, String name, UserSummary owner, String status, LocalDate startDate,
                             LocalDate endDate, int progress, int taskCount) {}

    public record ProjectCreate(@NotBlank @Pattern(regexp = "[A-Z]{2,10}", message = "Use de 2 a 10 letras mayúsculas") String code,
                                @NotBlank @Size(max = 120) String name,
                                @NotNull Long ownerId, LocalDate startDate, LocalDate endDate) {}

    public record ProjectUpdate(@NotBlank @Size(max = 120) String name, @NotNull Long ownerId,
                                @NotBlank @Pattern(regexp = PROJECT_STATUS, message = "Estado no válido") String status,
                                LocalDate startDate, LocalDate endDate) {}

    public record MembersRequest(@NotNull List<Long> userIds) {}

    public record TaskCreate(@NotBlank @Size(max = 200) String title,
                             @Pattern(regexp = PRIORITY, message = "Prioridad no válida") String priority,
                             Long assigneeId, LocalDate dueDate) {}

    public record TaskUpdate(@NotBlank @Size(max = 200) String title,
                             @NotBlank @Pattern(regexp = PRIORITY, message = "Prioridad no válida") String priority,
                             LocalDate dueDate) {}

    public record AssigneeRequest(Long assigneeId) {}

    public record MoveRequest(@NotBlank @Pattern(regexp = TASK_STATUS, message = "Estado no válido") String status,
                              @NotNull @Min(0) Integer position) {}

    public record ProgressRequest(@NotNull @Min(0) @Max(100) Integer progress) {}

    public record CommentRequest(@NotBlank @Size(max = 2000) String body) {}

    public record BoardColumn(String status, List<TaskView> tasks) {}
    public record BoardDto(long projectId, List<BoardColumn> columns) {}

    public record CommentDto(long id, UserSummary author, String body, Instant createdAt) {}

    public record AttachmentDto(long id, String fileName, String contentType, long sizeBytes, UserSummary uploadedBy,
                                Instant createdAt) {}
}
