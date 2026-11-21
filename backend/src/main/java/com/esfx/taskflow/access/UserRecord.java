package com.esfx.taskflow.access;

import java.time.Instant;

public record UserRecord(long id, String email, String passwordHash, String fullName, int roleId, String roleCode,
                         String roleName, boolean active, boolean mustChangePassword, Instant lastLoginAt) {}
