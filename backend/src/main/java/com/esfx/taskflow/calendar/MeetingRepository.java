package com.esfx.taskflow.calendar;

import com.esfx.taskflow.common.Db;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class MeetingRepository {
    private static final String SELECT = "SELECT m.id, m.title, m.start_at, m.end_at, m.location, m.organizer_id, m.project_id FROM calendar.meetings m";
    private static final String MINE = "(m.organizer_id = :uid OR EXISTS (SELECT 1 FROM calendar.meeting_invitees i WHERE i.meeting_id = m.id AND i.user_id = :uid))";

    private static final RowMapper<MeetingRow> MAPPER = (rs, n) -> new MeetingRow(rs.getLong("id"), rs.getString("title"),
            Db.instant(rs, "start_at"), Db.instant(rs, "end_at"), rs.getString("location"), rs.getLong("organizer_id"),
            Db.longOrNull(rs, "project_id"));

    private final JdbcClient jdbc;

    public MeetingRepository(JdbcClient jdbc) { this.jdbc = jdbc; }

    public Optional<MeetingRow> findById(long id) {
        return jdbc.sql(SELECT + " WHERE m.id = :id").param("id", id).query(MAPPER).optional();
    }

    public boolean isParticipant(long meetingId, long userId) {
        return jdbc.sql("SELECT count(*) FROM calendar.meetings m WHERE m.id = :id AND " + MINE)
                .param("id", meetingId).param("uid", userId).query(Long.class).single() > 0;
    }

    /**
     * Reuniones del rango. mode: "ME" (organizo o me invitaron), "PROJECTS" (además, las de los proyectos indicados),
     * "ALL" (todas).
     */
    public List<MeetingRow> inRange(Instant from, Instant to, String mode, long userId, Collection<Long> projectIds) {
        StringBuilder sql = new StringBuilder(SELECT).append(" WHERE m.start_at < :to AND m.end_at >= :from");
        boolean withProjects = mode.equals("PROJECTS") && !projectIds.isEmpty();
        if (mode.equals("ME") || (mode.equals("PROJECTS") && !withProjects)) sql.append(" AND ").append(MINE);
        if (withProjects) sql.append(" AND (").append(MINE).append(" OR m.project_id IN (:pids))");
        sql.append(" ORDER BY m.start_at, m.id");
        JdbcClient.StatementSpec spec = jdbc.sql(sql.toString()).param("from", Db.ts(from)).param("to", Db.ts(to));
        if (!mode.equals("ALL")) spec = spec.param("uid", userId);
        if (withProjects) spec = spec.param("pids", projectIds);
        return spec.query(MAPPER).list();
    }

    public List<MeetingRow> startingBetween(Instant from, Instant to) {
        return jdbc.sql(SELECT + " WHERE m.start_at >= :from AND m.start_at < :to ORDER BY m.start_at")
                .param("from", Db.ts(from)).param("to", Db.ts(to)).query(MAPPER).list();
    }

    public List<Long> participantsNotDeclined(long meetingId, long organizerId) {
        List<Long> ids = new java.util.ArrayList<>();
        ids.add(organizerId);
        ids.addAll(jdbc.sql("SELECT user_id FROM calendar.meeting_invitees WHERE meeting_id = :m AND response <> 'DECLINED'")
                .param("m", meetingId).query(Long.class).list());
        return ids;
    }

    public long insert(String title, Instant start, Instant end, String location, long organizerId, Long projectId) {
        return jdbc.sql("""
                INSERT INTO calendar.meetings (title, start_at, end_at, location, organizer_id, project_id)
                VALUES (:t, :s, :e, :l, :o, :p) RETURNING id
                """)
                .param("t", title).param("s", Db.ts(start)).param("e", Db.ts(end)).param("l", location)
                .param("o", organizerId).param("p", projectId).query(Long.class).single();
    }

    public void update(long id, String title, Instant start, Instant end, String location, Long projectId) {
        jdbc.sql("UPDATE calendar.meetings SET title = :t, start_at = :s, end_at = :e, location = :l, project_id = :p WHERE id = :id")
                .param("t", title).param("s", Db.ts(start)).param("e", Db.ts(end)).param("l", location)
                .param("p", projectId).param("id", id).update();
    }

    public void delete(long id) {
        jdbc.sql("DELETE FROM calendar.meetings WHERE id = :id").param("id", id).update();
    }

    // ---- invitados
    /** userId -> respuesta, en orden de invitación. */
    public Map<Long, String> invitees(long meetingId) {
        Map<Long, String> map = new LinkedHashMap<>();
        jdbc.sql("SELECT user_id, response FROM calendar.meeting_invitees WHERE meeting_id = :m ORDER BY user_id")
                .param("m", meetingId)
                .query((rs, n) -> {
                    map.put(rs.getLong("user_id"), rs.getString("response"));
                    return 0;
                }).list();
        return map;
    }

    public void addInvitee(long meetingId, long userId) {
        jdbc.sql("INSERT INTO calendar.meeting_invitees (meeting_id, user_id) VALUES (:m, :u) ON CONFLICT DO NOTHING")
                .param("m", meetingId).param("u", userId).update();
    }

    public void removeInvitee(long meetingId, long userId) {
        jdbc.sql("DELETE FROM calendar.meeting_invitees WHERE meeting_id = :m AND user_id = :u")
                .param("m", meetingId).param("u", userId).update();
    }

    public void respond(long meetingId, long userId, String response) {
        jdbc.sql("UPDATE calendar.meeting_invitees SET response = :r, responded_at = now() WHERE meeting_id = :m AND user_id = :u")
                .param("r", response).param("m", meetingId).param("u", userId).update();
    }
}
