package com.esfx.taskflow.projects;

import com.esfx.taskflow.common.Db;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public class ProjectRepository {
    private static final String SELECT = """
            SELECT p.id, p.code, p.name, p.owner_id, p.status, p.start_date, p.end_date,
                   (SELECT count(*) FROM projects.tasks t WHERE t.project_id = p.id) AS task_count,
                   (SELECT COALESCE(ROUND(AVG(t.progress)), 0) FROM projects.tasks t WHERE t.project_id = p.id) AS progress
            FROM projects.projects p
            """;
    private static final String VISIBLE = """
            (p.owner_id = :uid OR EXISTS (SELECT 1 FROM projects.project_members m WHERE m.project_id = p.id AND m.user_id = :uid))
            """;

    private static final RowMapper<ProjectRow> MAPPER = (rs, n) -> new ProjectRow(
            rs.getLong("id"), rs.getString("code"), rs.getString("name"), rs.getLong("owner_id"), rs.getString("status"),
            Db.date(rs, "start_date"), Db.date(rs, "end_date"), rs.getInt("task_count"), rs.getInt("progress"));

    private final JdbcClient jdbc;

    public ProjectRepository(JdbcClient jdbc) { this.jdbc = jdbc; }

    public Optional<ProjectRow> findById(long id) {
        return jdbc.sql(SELECT + " WHERE p.id = :id").param("id", id).query(MAPPER).optional();
    }

    public boolean isVisibleTo(long projectId, long userId) {
        return jdbc.sql("SELECT count(*) FROM projects.projects p WHERE p.id = :id AND " + VISIBLE)
                .param("id", projectId).param("uid", userId).query(Long.class).single() > 0;
    }

    public List<ProjectRow> search(Long visibleTo, String status, int limit, int offset) {
        StringBuilder sql = new StringBuilder(SELECT).append(" WHERE 1=1");
        if (visibleTo != null) sql.append(" AND ").append(VISIBLE);
        if (status != null) sql.append(" AND p.status = :status");
        sql.append(" ORDER BY p.created_at DESC, p.id DESC LIMIT :limit OFFSET :offset");
        JdbcClient.StatementSpec spec = jdbc.sql(sql.toString());
        if (visibleTo != null) spec = spec.param("uid", visibleTo);
        if (status != null) spec = spec.param("status", status);
        return spec.param("limit", limit).param("offset", offset).query(MAPPER).list();
    }

    public long count(Long visibleTo, String status) {
        StringBuilder sql = new StringBuilder("SELECT count(*) FROM projects.projects p WHERE 1=1");
        if (visibleTo != null) sql.append(" AND ").append(VISIBLE);
        if (status != null) sql.append(" AND p.status = :status");
        JdbcClient.StatementSpec spec = jdbc.sql(sql.toString());
        if (visibleTo != null) spec = spec.param("uid", visibleTo);
        if (status != null) spec = spec.param("status", status);
        return spec.query(Long.class).single();
    }

    public List<Long> visibleIds(Long visibleTo) {
        if (visibleTo == null) return jdbc.sql("SELECT p.id FROM projects.projects p").query(Long.class).list();
        return jdbc.sql("SELECT p.id FROM projects.projects p WHERE " + VISIBLE).param("uid", visibleTo).query(Long.class).list();
    }

    public boolean codeExists(String code) {
        return jdbc.sql("SELECT count(*) FROM projects.projects WHERE code = :c").param("c", code).query(Long.class).single() > 0;
    }

    public long insert(String code, String name, long ownerId, LocalDate start, LocalDate end) {
        return jdbc.sql("""
                INSERT INTO projects.projects (code, name, owner_id, status, start_date, end_date)
                VALUES (:code, :name, :owner, 'ACTIVE', :start, :end) RETURNING id
                """)
                .param("code", code).param("name", name).param("owner", ownerId)
                .param("start", start).param("end", end).query(Long.class).single();
    }

    public void update(long id, String name, long ownerId, String status, LocalDate start, LocalDate end) {
        jdbc.sql("""
                UPDATE projects.projects SET name = :name, owner_id = :owner, status = :status,
                       start_date = :start, end_date = :end WHERE id = :id
                """)
                .param("name", name).param("owner", ownerId).param("status", status)
                .param("start", start).param("end", end).param("id", id).update();
    }

    // ---- miembros
    public List<Long> memberIds(long projectId) {
        return jdbc.sql("SELECT user_id FROM projects.project_members WHERE project_id = :p ORDER BY joined_at, user_id")
                .param("p", projectId).query(Long.class).list();
    }

    public void replaceMembers(long projectId, Collection<Long> userIds) {
        jdbc.sql("DELETE FROM projects.project_members WHERE project_id = :p").param("p", projectId).update();
        for (Long uid : userIds) addMember(projectId, uid);
    }

    public void addMember(long projectId, long userId) {
        jdbc.sql("INSERT INTO projects.project_members (project_id, user_id) VALUES (:p, :u) ON CONFLICT DO NOTHING")
                .param("p", projectId).param("u", userId).update();
    }
}
