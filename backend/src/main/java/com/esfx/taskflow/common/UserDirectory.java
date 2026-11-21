package com.esfx.taskflow.common;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;

/**
 * API pública del módulo "access" para el resto de módulos.
 * Los demás módulos NO consultan las tablas de access directamente: solo usan esta interfaz.
 */
public interface UserDirectory {
    Optional<UserSummary> find(long userId);
    Map<Long, UserSummary> findAll(Collection<Long> userIds);
    boolean existsActive(long userId);
}
