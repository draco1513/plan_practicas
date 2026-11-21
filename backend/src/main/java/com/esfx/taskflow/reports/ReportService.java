package com.esfx.taskflow.reports;

import com.esfx.taskflow.common.*;
import com.esfx.taskflow.common.ProjectsApi.TaskView;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.*;

/**
 * Módulo de reportes. Es de solo lectura: consulta con SQL las tablas del esquema "projects" (única excepción
 * a la regla de no cruzar esquemas, porque un reporte necesita agregar datos) y nunca escribe en ellas.
 */
@Service
public class ReportService {
    public record StatusCount(String status, long count) {}
    public record AssigneeCount(UserSummary user, long todo, long inProgress, long done) {}
    public record ProjectCount(long projectId, String projectName, long total, long done, int progress) {}
    public record Dashboard(Instant generatedAt, List<StatusCount> tasksByStatus, List<AssigneeCount> tasksByAssignee,
                            List<ProjectCount> tasksByProject, List<TaskView> overdueTasks) {}

    private static final List<String> STATUSES = List.of("TODO", "IN_PROGRESS", "DONE");

    private final JdbcClient jdbc;
    private final ProjectsApi projects;
    private final UserDirectory directory;

    public ReportService(JdbcClient jdbc, ProjectsApi projects, UserDirectory directory) {
        this.jdbc = jdbc;
        this.projects = projects;
        this.directory = directory;
    }

    public Dashboard dashboard(Long projectId) {
        List<Long> visible = projects.visibleProjectIds();
        List<Long> ids;
        if (projectId != null) {
            if (!visible.contains(projectId)) throw ApiException.notFound("Proyecto");
            ids = List.of(projectId);
        } else {
            ids = visible;
        }
        if (ids.isEmpty()) {
            return new Dashboard(Instant.now(), STATUSES.stream().map(s -> new StatusCount(s, 0)).toList(), List.of(), List.of(), List.of());
        }

        Map<String, Long> byStatus = new HashMap<>();
        jdbc.sql("SELECT status, count(*) AS c FROM projects.tasks WHERE project_id IN (:ids) GROUP BY status")
                .param("ids", ids).query((rs, n) -> { byStatus.put(rs.getString("status"), rs.getLong("c")); return 0; }).list();
        List<StatusCount> statusCounts = STATUSES.stream().map(s -> new StatusCount(s, byStatus.getOrDefault(s, 0L))).toList();

        record AssigneeRow(long userId, long todo, long inProgress, long done) {}
        List<AssigneeRow> rows = jdbc.sql("""
                SELECT assignee_id,
                       count(*) FILTER (WHERE status = 'TODO') AS todo,
                       count(*) FILTER (WHERE status = 'IN_PROGRESS') AS in_progress,
                       count(*) FILTER (WHERE status = 'DONE') AS done
                FROM projects.tasks WHERE project_id IN (:ids) AND assignee_id IS NOT NULL
                GROUP BY assignee_id ORDER BY count(*) DESC
                """).param("ids", ids)
                .query((rs, n) -> new AssigneeRow(rs.getLong("assignee_id"), rs.getLong("todo"), rs.getLong("in_progress"), rs.getLong("done")))
                .list();
        Map<Long, UserSummary> users = directory.findAll(rows.stream().map(AssigneeRow::userId).toList());
        List<AssigneeCount> byAssignee = rows.stream()
                .map(r -> new AssigneeCount(users.getOrDefault(r.userId(), new UserSummary(r.userId(), "Usuario " + r.userId(), "")),
                        r.todo(), r.inProgress(), r.done()))
                .toList();

        List<ProjectCount> byProject = jdbc.sql("""
                SELECT p.id, p.name, count(t.id) AS total, count(t.id) FILTER (WHERE t.status = 'DONE') AS done,
                       COALESCE(ROUND(AVG(t.progress)), 0) AS progress
                FROM projects.projects p LEFT JOIN projects.tasks t ON t.project_id = p.id
                WHERE p.id IN (:ids) GROUP BY p.id, p.name ORDER BY p.name
                """).param("ids", ids)
                .query((rs, n) -> new ProjectCount(rs.getLong("id"), rs.getString("name"), rs.getLong("total"),
                        rs.getLong("done"), rs.getInt("progress")))
                .list();

        return new Dashboard(Instant.now(), statusCounts, byAssignee, byProject, projects.overdueTasks(ids, 20));
    }

    @RestController
    @RequestMapping("/api/reports")
    public static class Controller {
        private final ReportService service;

        public Controller(ReportService service) { this.service = service; }

        @GetMapping("/dashboard")
        @PreAuthorize("hasAnyAuthority('REPORT_VIEW_ALL','REPORT_VIEW_OWN')")
        public Dashboard dashboard(@RequestParam(required = false) Long projectId) { return service.dashboard(projectId); }
    }
}
