package com.esfx.taskflow.timetracking;

import com.esfx.taskflow.common.Db;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public class TimeRepository {
    public record Row(long id, long taskId, long projectId, long userId, LocalDate workDate, double hours, String note) {}

    private static final RowMapper<Row> MAPPER = (rs, n) -> new Row(rs.getLong("id"), rs.getLong("task_id"), rs.getLong("project_id"),
            rs.getLong("user_id"), Db.date(rs, "work_date"), rs.getBigDecimal("hours").doubleValue(), rs.getString("note"));

    private final JdbcClient jdbc;

    public TimeRepository(JdbcClient jdbc) { this.jdbc = jdbc; }

    public Optional<Row> findById(long id) {
        return jdbc.sql("SELECT * FROM timetracking.time_entries WHERE id = :id").param("id", id).query(MAPPER).optional();
    }

    private String where(Collection<Long> projectIds, Long userId, Long projectId, Long taskId, LocalDate from, LocalDate to, List<Object[]> params) {
        StringBuilder sb = new StringBuilder(" WHERE project_id IN (:pids)");
        params.add(new Object[]{"pids", projectIds});
        if (userId != null) { sb.append(" AND user_id = :uid"); params.add(new Object[]{"uid", userId}); }
        if (projectId != null) { sb.append(" AND project_id = :pid"); params.add(new Object[]{"pid", projectId}); }
        if (taskId != null) { sb.append(" AND task_id = :tid"); params.add(new Object[]{"tid", taskId}); }
        if (from != null) { sb.append(" AND work_date >= :from"); params.add(new Object[]{"from", from}); }
        if (to != null) { sb.append(" AND work_date <= :to"); params.add(new Object[]{"to", to}); }
        return sb.toString();
    }

    public List<Row> search(Collection<Long> projectIds, Long userId, Long projectId, Long taskId, LocalDate from, LocalDate to,
                            int limit, int offset) {
        List<Object[]> params = new ArrayList<>();
        String w = where(projectIds, userId, projectId, taskId, from, to, params);
        JdbcClient.StatementSpec spec = jdbc.sql("SELECT * FROM timetracking.time_entries" + w + " ORDER BY work_date DESC, id DESC LIMIT :limit OFFSET :offset");
        for (Object[] p : params) spec = spec.param((String) p[0], p[1]);
        return spec.param("limit", limit).param("offset", offset).query(MAPPER).list();
    }

    public long count(Collection<Long> projectIds, Long userId, Long projectId, Long taskId, LocalDate from, LocalDate to) {
        List<Object[]> params = new ArrayList<>();
        String w = where(projectIds, userId, projectId, taskId, from, to, params);
        JdbcClient.StatementSpec spec = jdbc.sql("SELECT count(*) FROM timetracking.time_entries" + w);
        for (Object[] p : params) spec = spec.param((String) p[0], p[1]);
        return spec.query(Long.class).single();
    }

    public long insert(long taskId, long projectId, long userId, LocalDate date, double hours, String note) {
        return jdbc.sql("""
                INSERT INTO timetracking.time_entries (task_id, project_id, user_id, work_date, hours, note)
                VALUES (:t, :p, :u, :d, :h, :n) RETURNING id
                """)
                .param("t", taskId).param("p", projectId).param("u", userId).param("d", date).param("h", BigDecimal.valueOf(hours))
                .param("n", note).query(Long.class).single();
    }


    public void update(long id, long taskId, long projectId, LocalDate date, double hours, String note) {
        jdbc.sql("UPDATE timetracking.time_entries SET task_id = :t, project_id = :p, work_date = :d, hours = :h, note = :n WHERE id = :id")
                .param("t", taskId).param("p", projectId).param("d", date).param("h", BigDecimal.valueOf(hours))
                .param("n", note).param("id", id).update();
    }

    public void delete(long id) {
        jdbc.sql("DELETE FROM timetracking.time_entries WHERE id = :id").param("id", id).update();
    }

    public record UserTotal(long userId, double hours) {}
    public record TaskTotal(long taskId, double hours) {}

    public double total(long projectId) {
        return jdbc.sql("SELECT COALESCE(SUM(hours), 0) FROM timetracking.time_entries WHERE project_id = :p")
                .param("p", projectId).query(BigDecimal.class).single().doubleValue();
    }

    public List<UserTotal> byUser(long projectId) {
        return jdbc.sql("SELECT user_id, SUM(hours) AS h FROM timetracking.time_entries WHERE project_id = :p GROUP BY user_id ORDER BY h DESC")
                .param("p", projectId)
                .query((rs, n) -> new UserTotal(rs.getLong("user_id"), rs.getBigDecimal("h").doubleValue())).list();
    }

    public List<TaskTotal> byTask(long projectId) {
        return jdbc.sql("SELECT task_id, SUM(hours) AS h FROM timetracking.time_entries WHERE project_id = :p GROUP BY task_id ORDER BY h DESC")
                .param("p", projectId)
                .query((rs, n) -> new TaskTotal(rs.getLong("task_id"), rs.getBigDecimal("h").doubleValue())).list();
    }
}
