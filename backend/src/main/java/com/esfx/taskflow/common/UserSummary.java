package com.esfx.taskflow.common;

/** Datos mínimos y públicos de una persona (nunca incluye la contraseña). */
public record UserSummary(long id, String fullName, String email) {}
