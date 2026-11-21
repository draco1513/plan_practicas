package com.esfx.taskflow.projects;

import com.esfx.taskflow.common.Db;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public class TaskRepository {
    private static final String SELECT = """
            SELECT t.id, t.project_id, p.code, t.task_number, t.title, t.status, t.priority, t.assignee_id,
                   t.reporter_id, t.due_date, t.progress, t.position, t.created_at, t.updated_at
            FROM projects.tasks t JOIN projects.projects p ON p.id = t.project_id
            """;

    private static final RowMapper<TaskRow> MAPPER = (rs, n) -> new TaskRow(
            rs.getLong("id"), rs.getLong("project_id"), rs.getString("code"), rs.getInt("task_number"),
            rs.getString("title"), rs.getString("status"), rs.getString("priority"), Db.longOrNull(rs, "assignee_id"),
            rs.getLong("reporter_id"), Db.date(rs, "due_date"), rs.getInt("progress"), rs.getInt("position"),
            Db.instant(rs, "created_at"), Db.instant(rs, "updated_at"));

    private final JdbcClient jdbc;

    public TaskRepository(JdbcClient jdbc) { this.jdbc = jdbc; }

    public Optional<TaskRow> findById(long id) {
        return jdbc.sql(SELECT + " WHERE t.id = :id").param("id", id).query(MAPPER).optional();
    }

    public List<TaskRow> findByIds(Collection<Long> ids) {
        if (ids.isEmpty()) return List.of();
        return jdbc.sql(SELECT + " WHERE t.id IN (:ids)").param("ids", ids).query(MAPPER).list();
    }

    public List<TaskRow> board(long projectId) {
        return jdbc.sql(SELECT + " WHERE t.project_id = :p ORDER BY t.position, t.task_number")
                .param("p", projectId).query(MAPPER).list();
    }

    public List<TaskRow> search(long projectId, String status, Long assigneeId, boolean overdueOnly, LocalDate today,
                                int limit, int offset) {
        List<Object[]> params = new ArrayList<>();
        String where = where(projectId, status, assigneeId, overdueOnly, today, params);
        JdbcClient.StatementSpec spec = jdbc.sql(SELECT + where + " ORDER BY t.task_number DESC LIMIT :limit OFFSET :offset");
        for (Object[] p : params) spec = spec.param((String) p[0], p[1]);
        return spec.param("limit", limit).param("offset", offset).query(MAPPER).list();
    }

    public long count(long projectId, String status, Long assigneeId, boolean overdueOnly, LocalDate today) {
        List<Object[]> params = new ArrayList<>();
        String where = where(projectId, status, assigneeId, overdueOnly, today, params);
        JdbcClient.StatementSpec spec = jdbc.sql("SELECT count(*) FROM projects.tasks t JOIN projects.projects p ON p.id = t.project_id" + where);
        for (Object[] p : params) spec = spec.param((String) p[0], p[1]);
        return spec.query(Long.class).single();
    }

    private String where(long projectId, String status, Long assigneeId, boolean overdueOnly, LocalDate today, List<Object[]> params) {
        StringBuilder sb = new StringBuilder(" WHERE t.project_id = :project");
        params.add(new Object[]{"project", projectId});
        if (status != null) { sb.append(" AND t.status = :status"); params.add(new Object[]{"status", status}); }
        if (assigneeId != null) { sb.append(" AND t.assignee_id = :assignee"); params.add(new Object[]{"assignee", assigneeId}); }
        if (overdueOnly) { sb.append(" AND t.status <> 'DONE' AND t.due_date < :today"); params.add(new Object[]{"today", today}); }
        return sb.toString();
    }

    public List<TaskRow> overdue(Collection<Long> projectIds, LocalDate today, int limit) {
        if (projectIds.isEmpty()) return List.of();
        return jdbc.sql(SELECT + " WHERE t.project_id IN (:ids) AND t.status <> 'DONE' AND t.due_date < :today ORDER BY t.due_date, t.id LIMIT :limit")
                .param("ids", projectIds).param("today", today).param("limit", limit).query(MAPPER).list();
    }

    public List<TaskRow> dueBetween(Collection<Long> projectIds, LocalDate from, LocalDate to, Long assigneeId) {
        if (projectIds.isEmpty()) return List.of();
        String sql = SELECT + " WHERE t.project_id IN (:ids) AND t.status <> 'DONE' AND t.due_date BETWEEN :from AND :to"
                + (assigneeId != null ? " AND t.assignee_id = :assignee" : "") + " ORDER BY t.due_date, t.id";
        JdbcClient.StatementSpec spec = jdbc.sql(sql).param("ids", projectIds).param("from", from).param("to", to);
        if (assigneeId != null) spec = spec.param("assignee", assigneeId);
        return spec.query(MAPPER).list();
    }

    public List<TaskRow> assignedDueUntil(LocalDate from, LocalDate until) {
        return jdbc.sql(SELECT + " WHERE t.status <> 'DONE' AND t.assignee_id IS NOT NULL AND t.due_date BETWEEN :from AND :until ORDER BY t.due_date, t.id")
                .param("from", from).param("until", until).query(MAPPER).list();
    }

    /** Siguiente número de tarea del proyecto, con bloqueo de fila para que dos peticiones no repitan el número. */
    public int nextTaskNumber(long projectId) {
        jdbc.sql("SELECT id FROM projects.projects WHERE id = :id FOR UPDATE").param("id", projectId).query(Long.class).single();
        return jdbc.sql("UPDATE projects.projects SET task_seq = task_seq + 1 WHERE id = :id RETURNING task_seq")
                .param("id", projectId).query(Integer.class).single();
    }

    public int maxPosition(long projectId, String status) {
        return jdbc.sql("SELECT COALESCE(MAX(position), -1) FROM projects.tasks WHERE project_id = :p AND status = :s")
                .param("p", projectId).param("s", status).query(Integer.class).single();
    }

    public long insert(long projectId, int number, String title, String priority, Long assigneeId, long reporterId,
                       LocalDate due, int position) {
        return jdbc.sql("""
                INSERT INTO projects.tasks (project_id, task_number, title, priority, assignee_id, reporter_id, due_date, position)
                VALUES (:p, :n, :title, :prio, :assignee, :reporter, :due, :pos) RETURNING id
                """)
                .param("p", projectId).param("n", number).param("title", title).param("prio", priority)
                .param("assignee", assigneeId).param("reporter", reporterId).param("due", due).param("pos", position)
                .query(Long.class).single();
    }

    public void update(long id, String title, String priority, LocalDate due) {
        jdbc.sql("UPDATE projects.tasks SET title = :t, priority = :p, due_date = :d WHERE id = :id")
                .param("t", title).param("p", priority).param("d", due).param("id", id).update();
    }

    public void setAssignee(long id, Long assigneeId) {
        jdbc.sql("UPDATE projects.tasks SET assignee_id = :a WHERE id = :id").param("a", assigneeId).param("id", id).update();
    }

    public void setStatusProgress(long id, String status, int progress) {
        jdbc.sql("UPDATE projects.tasks SET status = :s, progress = :p WHERE id = :id")
                .param("s", status).param("p", progress).param("id", id).update();
    }

    public List<Long> columnIds(long projectId, String status, long excludeId) {
        return jdbc.sql("SELECT id FROM projects.tasks WHERE project_id = :p AND status = :s AND id <> :x ORDER BY position, task_number")
                .param("p", projectId).param("s", status).param("x", excludeId).query(Long.class).list();
    }

    public void setPosition(long id, int position) {
        jdbc.sql("UPDATE projects.tasks SET position = :pos WHERE id = :id").param("pos", position).param("id", id).update();
    }

    // ---- comentarios
    public record CommentRow(long id, long authorId, String body, java.time.Instant createdAt) {}

    public List<CommentRow> comments(long taskId) {
        return jdbc.sql("SELECT id, author_id, body, created_at FROM projects.task_comments WHERE task_id = :t ORDER BY created_at, id")
                .param("t", taskId)
                .query((rs, n) -> new CommentRow(rs.getLong("id"), rs.getLong("author_id"), rs.getString("body"), Db.instant(rs, "created_at")))
                .list();
    }

    public CommentRow addComment(long taskId, long authorId, String body) {
        return jdbc.sql("""
                INSERT INTO projects.task_comments (task_id, author_id, body) VALUES (:t, :a, :b)
                RETURNING id, author_id, body, created_at
                """)
                .param("t", taskId).param("a", authorId).param("b", body)
                .query((rs, n) -> new CommentRow(rs.getLong("id"), rs.getLong("author_id"), rs.getString("body"), Db.instant(rs, "created_at")))
                .single();
    }

    // ---- adjuntos
    public record AttachmentRow(long id, long taskId, long uploadedBy, String fileName, String contentType, long sizeBytes,
                                String storagePath, java.time.Instant createdAt) {}

    private static final RowMapper<AttachmentRow> ATT = (rs, n) -> new AttachmentRow(rs.getLong("id"), rs.getLong("task_id"),
            rs.getLong("uploaded_by"), rs.getString("file_name"), rs.getString("content_type"), rs.getLong("size_bytes"),
            rs.getString("storage_path"), Db.instant(rs, "created_at"));

    public List<AttachmentRow> attachments(long taskId) {
        return jdbc.sql("SELECT * FROM projects.task_attachments WHERE task_id = :t ORDER BY created_at, id")
                .param("t", taskId).query(ATT).list();
    }

    public Optional<AttachmentRow> attachment(long id) {
        return jdbc.sql("SELECT * FROM projects.task_attachments WHERE id = :id").param("id", id).query(ATT).optional();
    }

    public AttachmentRow addAttachment(long taskId, long uploadedBy, String name, String type, long size, String path) {
        return jdbc.sql("""
                INSERT INTO projects.task_attachments (task_id, uploaded_by, file_name, content_type, size_bytes, storage_path)
                VALUES (:t, :u, :n, :c, :s, :p) RETURNING *
                """)
                .param("t", taskId).param("u", uploadedBy).param("n", name).param("c", type).param("s", size).param("p", path)
                .query(ATT).single();
    }
}
