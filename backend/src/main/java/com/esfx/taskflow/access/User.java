package com.esfx.taskflow.access;

import java.time.Instant;

/** Representa un usuario en la capa de dominio (sin contraseña). */
public record User(long id, String email, String fullName, int roleId, boolean active,
                    boolean mustChangePassword, Instant lastLoginAt) {}
