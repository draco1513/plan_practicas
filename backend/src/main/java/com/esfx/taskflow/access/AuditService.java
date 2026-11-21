package com.esfx.taskflow.access;

import com.esfx.taskflow.common.AuditPort;
import com.esfx.taskflow.common.Db;
import com.esfx.taskflow.common.PageResponse;
import com.esfx.taskflow.common.Paging;
import com.esfx.taskflow.common.UserSummary;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class AuditService implements AuditPort {
    private static final Logger log = LoggerFactory.getLogger(AuditService.class);
    private static final UserSummary SYSTEM = new UserSummary(0, "Sistema", "");

    private final JdbcClient jdbc;
    private final ObjectMapper mapper;

    public AuditService(JdbcClient jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    @Override
    public void record(Long actorUserId, String action, String entityType, Long entityId, Map<String, Object> detail) {
        try {
            String json = detail == null ? null : mapper.writeValueAsString(detail);
            jdbc.sql("""
                    INSERT INTO access.audit_log (actor_user_id, action, entity_type, entity_id, detail)
                    VALUES (:actor, :action, :type, :entity, CAST(:detail AS jsonb))
                    """)
                    .param("actor", actorUserId).param("action", action).param("type", entityType)
                    .param("entity", entityId).param("detail", json).update();
        } catch (JsonProcessingException | RuntimeException e) {
            // La auditoría nunca debe romper la operación principal
            log.warn("No se pudo registrar la auditoría {} {}: {}", action, entityType, e.getMessage());
        }
    }

    public PageResponse<AccessDtos.AuditLogDto> search(String entityType, Long actorUserId, Instant from, Instant to, Paging paging) {
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        List<Object[]> params = new ArrayList<>();
        if (entityType != null && !entityType.isBlank()) { where.append(" AND a.entity_type = :etype"); params.add(new Object[]{"etype", entityType}); }
        if (actorUserId != null) { where.append(" AND a.actor_user_id = :actor"); params.add(new Object[]{"actor", actorUserId}); }
        if (from != null) { where.append(" AND a.created_at >= :from"); params.add(new Object[]{"from", Db.ts(from)}); }
        if (to != null) { where.append(" AND a.created_at < :to"); params.add(new Object[]{"to", Db.ts(to)}); }

        JdbcClient.StatementSpec count = jdbc.sql("SELECT count(*) FROM access.audit_log a" + where);
        for (Object[] p : params) count = count.param((String) p[0], p[1]);
        long total = count.query(Long.class).single();

        RowMapper<AccessDtos.AuditLogDto> mapperRow = (rs, n) -> {
            Long actorId = Db.longOrNull(rs, "actor_user_id");
            UserSummary actor = actorId == null ? SYSTEM
                    : new UserSummary(actorId, rs.getString("full_name"), rs.getString("email"));
            return new AccessDtos.AuditLogDto(rs.getLong("id"), actor, rs.getString("action"), rs.getString("entity_type"),
                    Db.longOrNull(rs, "entity_id"), parse(rs.getString("detail")), Db.instant(rs, "created_at"));
        };
        JdbcClient.StatementSpec list = jdbc.sql("""
                SELECT a.id, a.actor_user_id, u.full_name, u.email, a.action, a.entity_type, a.entity_id,
                       a.detail::text AS detail, a.created_at
                FROM access.audit_log a LEFT JOIN access.users u ON u.id = a.actor_user_id
                """ + where + " ORDER BY a.created_at DESC, a.id DESC LIMIT :limit OFFSET :offset");
        for (Object[] p : params) list = list.param((String) p[0], p[1]);
        List<AccessDtos.AuditLogDto> rows = list.param("limit", paging.size()).param("offset", paging.offset()).query(mapperRow).list();
        return PageResponse.of(rows, paging.page(), paging.size(), total);
    }

    private Map<String, Object> parse(String json) {
        if (json == null) return null;
        try {
            return mapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (JsonProcessingException e) {
            return null;
        }
    }
}
