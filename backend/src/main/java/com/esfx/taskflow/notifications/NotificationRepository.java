package com.esfx.taskflow.notifications;

import com.esfx.taskflow.common.Db;
import com.esfx.taskflow.notifications.NotificationDtos.PreferencesDto;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public class NotificationRepository {
    public record Template(String subject, String body) {}

    public record Row(long id, long userId, String recipientEmail, String type, String channel, String title, String body,
                      String payloadJson, String status, int attempts, Instant createdAt, Instant readAt) {}

    private static final String SELECT = """
            SELECT id, user_id, recipient_email, type, channel, title, body, payload::text AS payload, status, attempts,
                   created_at, read_at FROM notifications.notifications
            """;

    private static final RowMapper<Row> MAPPER = (rs, n) -> new Row(rs.getLong("id"), rs.getLong("user_id"),
            rs.getString("recipient_email"), rs.getString("type"), rs.getString("channel"), rs.getString("title"),
            rs.getString("body"), rs.getString("payload"), rs.getString("status"), rs.getInt("attempts"),
            Db.instant(rs, "created_at"), Db.instant(rs, "read_at"));

    private final JdbcClient jdbc;

    public NotificationRepository(JdbcClient jdbc) { this.jdbc = jdbc; }

    public Optional<Template> template(String code, String channel) {
        return jdbc.sql("SELECT subject_template, body_template FROM notifications.notification_templates WHERE code = :c AND channel = :ch")
                .param("c", code).param("ch", channel)
                .query((rs, n) -> new Template(rs.getString("subject_template"), rs.getString("body_template"))).optional();
    }

    /** Inserta la notificación; si la clave de idempotencia ya existe devuelve vacío (no se duplica). */
    public Optional<Long> insert(long userId, String email, String type, String channel, String title, String body,
                                 String payloadJson, String status, String idempotencyKey, Instant scheduledAt) {
        return jdbc.sql("""
                INSERT INTO notifications.notifications
                    (user_id, recipient_email, type, channel, title, body, payload, status, idempotency_key, scheduled_at, sent_at)
                VALUES (:u, :email, :type, :ch, :title, :body, CAST(:payload AS jsonb), :status, :key, :sched,
                        CASE WHEN :status = 'SENT' THEN now() ELSE NULL END)
                ON CONFLICT (idempotency_key) DO NOTHING
                RETURNING id
                """)
                .param("u", userId).param("email", email).param("type", type).param("ch", channel)
                .param("title", title).param("body", body).param("payload", payloadJson).param("status", status)
                .param("key", idempotencyKey).param("sched", Db.ts(scheduledAt))
                .query(Long.class).optional();
    }

    public Optional<Row> findById(long id) {
        return jdbc.sql(SELECT + " WHERE id = :id").param("id", id).query(MAPPER).optional();
    }

    /** Correos pendientes que la cola de Redis no entregó (por ejemplo, porque Redis estaba caído). */
    public List<Long> stalePendingEmails(int olderThanSeconds, int limit) {
        return jdbc.sql("""
                SELECT id FROM notifications.notifications
                WHERE channel = 'EMAIL' AND status = 'PENDING' AND scheduled_at <= now() - make_interval(secs => :secs)
                ORDER BY scheduled_at LIMIT :limit
                """).param("secs", olderThanSeconds).param("limit", limit).query(Long.class).list();
    }

    public void markSent(long id) {
        jdbc.sql("UPDATE notifications.notifications SET status = 'SENT', sent_at = now(), attempts = attempts + 1, last_error = NULL WHERE id = :id")
                .param("id", id).update();
    }

    /** Registra el intento fallido; si se agotan los intentos pasa a FAILED, si no queda PENDING para reintentar. */
    public void markAttemptFailed(long id, String error, int maxAttempts) {
        String msg = error == null ? "Error desconocido" : (error.length() > 480 ? error.substring(0, 480) : error);
        jdbc.sql("""
                UPDATE notifications.notifications
                SET attempts = attempts + 1, last_error = :err, scheduled_at = now(),
                    status = CASE WHEN attempts + 1 >= :max THEN 'FAILED' ELSE 'PENDING' END
                WHERE id = :id
                """).param("err", msg).param("max", maxAttempts).param("id", id).update();
    }

    // ---- bandeja del usuario (solo canal IN_APP)
    public List<Row> inbox(long userId, boolean unreadOnly, int limit, int offset) {
        return jdbc.sql(SELECT + " WHERE user_id = :u AND channel = 'IN_APP'" + (unreadOnly ? " AND read_at IS NULL" : "")
                + " ORDER BY created_at DESC, id DESC LIMIT :limit OFFSET :offset")
                .param("u", userId).param("limit", limit).param("offset", offset).query(MAPPER).list();
    }

    public long inboxCount(long userId, boolean unreadOnly) {
        return jdbc.sql("SELECT count(*) FROM notifications.notifications WHERE user_id = :u AND channel = 'IN_APP'"
                + (unreadOnly ? " AND read_at IS NULL" : "")).param("u", userId).query(Long.class).single();
    }

    public boolean markRead(long id, long userId) {
        return jdbc.sql("UPDATE notifications.notifications SET read_at = COALESCE(read_at, now()) WHERE id = :id AND user_id = :u AND channel = 'IN_APP'")
                .param("id", id).param("u", userId).update() > 0;
    }

    public void markAllRead(long userId) {
        jdbc.sql("UPDATE notifications.notifications SET read_at = now() WHERE user_id = :u AND channel = 'IN_APP' AND read_at IS NULL")
                .param("u", userId).update();
    }

    // ---- preferencias
    public PreferencesDto preferences(long userId) {
        return jdbc.sql("SELECT email_enabled, meeting_reminder_min, task_due_reminder_hrs FROM notifications.notification_preferences WHERE user_id = :u")
                .param("u", userId)
                .query((rs, n) -> new PreferencesDto(rs.getBoolean("email_enabled"), rs.getInt("meeting_reminder_min"), rs.getInt("task_due_reminder_hrs")))
                .optional().orElse(new PreferencesDto(true, 30, 24));
    }

    public void savePreferences(long userId, PreferencesDto p) {
        jdbc.sql("""
                INSERT INTO notifications.notification_preferences (user_id, email_enabled, meeting_reminder_min, task_due_reminder_hrs)
                VALUES (:u, :e, :m, :t)
                ON CONFLICT (user_id) DO UPDATE SET email_enabled = :e, meeting_reminder_min = :m, task_due_reminder_hrs = :t
                """)
                .param("u", userId).param("e", p.emailEnabled()).param("m", p.meetingReminderMin()).param("t", p.taskDueReminderHrs())
                .update();
    }
}
