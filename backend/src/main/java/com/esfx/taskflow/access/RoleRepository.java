package com.esfx.taskflow.access;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class RoleRepository {
    private static final String BASE = "SELECT id, code, name FROM access.roles ORDER BY id";
    private final JdbcClient jdbc;

    public RoleRepository(JdbcClient jdbc) { this.jdbc = jdbc; }

    public List<Role> findAll() {
        return jdbc.sql(BASE)
                .query((rs, n) -> new Role(rs.getInt("id"), rs.getString("code"), rs.getString("name")))
                .list();
    }
}
