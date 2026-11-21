package com.esfx.taskflow.common;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * API pública del módulo "projects" para los demás módulos (calendario, reportes, horas).
 * Así ningún otro módulo toca las tablas del esquema "projects".
 */
public interface ProjectsApi {

    /** Tarea tal como la ve la API (también es el contrato JSON de "Task"). */
    record TaskView(long id, String key, long projectId, String title, String status, String priority,
                    UserSummary assignee, UserSummary reporter, LocalDate dueDate, boolean overdue,
                    int progress, int position, Instant createdAt, Instant updatedAt) {}

    /** ¿El usuario actual puede ver este proyecto? */
    boolean canView(long projectId);

    /** Proyectos que el usuario actual puede ver (todos si tiene permiso global). */
    List<Long> visibleProjectIds();

    Optional<TaskView> task(long taskId);

    Map<Long, TaskView> tasks(Collection<Long> taskIds);

    /** Tareas vencidas (no terminadas) de los proyectos indicados. */
    List<TaskView> overdueTasks(Collection<Long> projectIds, int limit);

    /** Tareas no terminadas con fecha límite en el rango, de los proyectos indicados y opcionalmente de un responsable. */
    List<TaskView> tasksDueBetween(Collection<Long> projectIds, LocalDate from, LocalDate to, Long assigneeId);

    /** Para tareas programadas (sin usuario en sesión): tareas no terminadas con responsable y fecha límite hasta el día indicado. */
    List<TaskView> assignedTasksDueUntil(LocalDate until);
}
