package com.esfx.taskflow.access;

import com.esfx.taskflow.common.Db;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public class UserRepository {
    private static final String BASE = """
            SELECT u.id, u.email, u.password_hash, u.full_name, u.role_id, r.code AS role_code, r.name AS role_name,
                   u.active, u.must_change_password, u.last_login_at
            FROM access.users u JOIN access.roles r ON r.id = u.role_id
            """;

    private static final RowMapper<UserRecord> MAPPER = (rs, n) -> new UserRecord(
            rs.getLong("id"), rs.getString("email"), rs.getString("password_hash"), rs.getString("full_name"),
            rs.getInt("role_id"), rs.getString("role_code"), rs.getString("role_name"),
            rs.getBoolean("active"), rs.getBoolean("must_change_password"), Db.instant(rs, "last_login_at"));

    private final JdbcClient jdbc;

    public UserRepository(JdbcClient jdbc) { this.jdbc = jdbc; }

    public Optional<UserRecord> findById(long id) {
        return jdbc.sql(BASE + " WHERE u.id = :id").param("id", id).query(MAPPER).optional();
    }

    public Optional<UserRecord> findByEmail(String email) {
        return jdbc.sql(BASE + " WHERE lower(u.email) = lower(:email)").param("email", email).query(MAPPER).optional();
    }

    public boolean emailExists(String email) {
        return jdbc.sql("SELECT count(*) FROM access.users WHERE lower(email) = lower(:email)")
                .param("email", email).query(Long.class).single() > 0;
    }

    public long countAll() {
        return jdbc.sql("SELECT count(*) FROM access.users").query(Long.class).single();
    }

    public List<UserRecord> findByIds(java.util.Collection<Long> ids) {
        if (ids.isEmpty()) return List.of();
        return jdbc.sql(BASE + " WHERE u.id IN (:ids)").param("ids", ids).query(MAPPER).list();
    }

    public List<UserRecord> search(String q, Boolean active, int limit, int offset) {
        StringBuilder sql = new StringBuilder(BASE).append(" WHERE 1=1");
        List<Object[]> params = new ArrayList<>();
        appendFilters(sql, params, q, active);
        sql.append(" ORDER BY lower(u.full_name) LIMIT :limit OFFSET :offset");
        JdbcClient.StatementSpec spec = jdbc.sql(sql.toString());
        for (Object[] p : params) spec = spec.param((String) p[0], p[1]);
        return spec.param("limit", limit).param("offset", offset).query(MAPPER).list();
    }

    public long count(String q, Boolean active) {
        StringBuilder sql = new StringBuilder("SELECT count(*) FROM access.users u WHERE 1=1");
        List<Object[]> params = new ArrayList<>();
        appendFilters(sql, params, q, active);
        JdbcClient.StatementSpec spec = jdbc.sql(sql.toString());
        for (Object[] p : params) spec = spec.param((String) p[0], p[1]);
        return spec.query(Long.class).single();
    }

    private void appendFilters(StringBuilder sql, List<Object[]> params, String q, Boolean active) {
        if (q != null && !q.isBlank()) {
            sql.append(" AND (lower(u.full_name) LIKE :q OR lower(u.email) LIKE :q)");
            params.add(new Object[]{"q", "%" + q.trim().toLowerCase() + "%"});
        }
        if (active != null) {
            sql.append(" AND u.active = :active");
            params.add(new Object[]{"active", active});
        }
    }

    public long insert(String email, String passwordHash, String fullName, int roleId, boolean mustChange) {
        return jdbc.sql("""
                INSERT INTO access.users (email, password_hash, full_name, role_id, must_change_password)
                VALUES (:email, :hash, :name, :role, :must) RETURNING id
                """)
                .param("email", email).param("hash", passwordHash).param("name", fullName)
                .param("role", roleId).param("must", mustChange)
                .query(Long.class).single();
    }

    public void updateProfile(long id, String fullName, int roleId) {
        jdbc.sql("UPDATE access.users SET full_name = :name, role_id = :role WHERE id = :id")
                .param("name", fullName).param("role", roleId).param("id", id).update();
    }

    public void setActive(long id, boolean active) {
        jdbc.sql("UPDATE access.users SET active = :a WHERE id = :id").param("a", active).param("id", id).update();
    }

    public void setPassword(long id, String hash, boolean mustChange) {
        jdbc.sql("UPDATE access.users SET password_hash = :h, must_change_password = :m WHERE id = :id")
                .param("h", hash).param("m", mustChange).param("id", id).update();
    }

    public void touchLogin(long id) {
        jdbc.sql("UPDATE access.users SET last_login_at = now() WHERE id = :id").param("id", id).update();
    }

    public Set<String> permissionsOfRole(int roleId) {
        return Set.copyOf(jdbc.sql("""
                SELECT p.code FROM access.role_permissions rp JOIN access.permissions p ON p.id = rp.permission_id
                WHERE rp.role_id = :r
                """).param("r", roleId).query(String.class).list());
    }

    public List<RoleRow> roles() {
        return jdbc.sql("SELECT id, code, name FROM access.roles ORDER BY id")
                .query((rs, n) -> new RoleRow(rs.getInt("id"), rs.getString("code"), rs.getString("name"))).list();
    }

    public boolean roleExists(int roleId) {
        return jdbc.sql("SELECT count(*) FROM access.roles WHERE id = :id").param("id", roleId).query(Long.class).single() > 0;
    }

    public Optional<Integer> roleIdByCode(String code) {
        return jdbc.sql("SELECT id FROM access.roles WHERE code = :c").param("c", code).query(Integer.class).optional();
    }

    // ---- recuperación de contraseña
    public void insertResetToken(long userId, String tokenHash, Instant expiresAt) {
        jdbc.sql("INSERT INTO access.password_reset_tokens (user_id, token_hash, expires_at) VALUES (:u, :h, :e)")
                .param("u", userId).param("h", tokenHash).param("e", Db.ts(expiresAt)).update();
    }

    /** Devuelve el id de usuario si el token existe, no se usó y no venció. */
    public Optional<Long> findValidResetToken(String tokenHash) {
        return jdbc.sql("""
                SELECT user_id FROM access.password_reset_tokens
                WHERE token_hash = :h AND used_at IS NULL AND expires_at > now()
                """).param("h", tokenHash).query(Long.class).optional();
    }

    public void markResetTokenUsed(String tokenHash) {
        jdbc.sql("UPDATE access.password_reset_tokens SET used_at = now() WHERE token_hash = :h")
                .param("h", tokenHash).update();
    }

    public record RoleRow(int id, String code, String name) {}
}
