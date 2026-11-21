package com.esfx.taskflow.common;

import java.util.Map;

/** API pública para registrar auditoría; la implementa el módulo "access". */
public interface AuditPort {
    void record(Long actorUserId, String action, String entityType, Long entityId, Map<String, Object> detail);
}
