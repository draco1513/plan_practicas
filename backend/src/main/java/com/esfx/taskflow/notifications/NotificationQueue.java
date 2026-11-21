package com.esfx.taskflow.notifications;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Cola de correos pendientes en Redis (lista). Si Redis falla no se pierde nada: la notificación ya está
 * guardada como PENDING en PostgreSQL y el barrido periódico la envía.
 */
@Component
public class NotificationQueue {
    private static final Logger log = LoggerFactory.getLogger(NotificationQueue.class);

    private final StringRedisTemplate redis;
    private final String key;

    public NotificationQueue(StringRedisTemplate redis, @Value("${app.notifications.queue-key}") String key) {
        this.redis = redis;
        this.key = key;
    }

    public void push(long notificationId) {
        try {
            redis.opsForList().leftPush(key, Long.toString(notificationId));
        } catch (RuntimeException e) {
            log.warn("Redis no disponible; la notificación {} la enviará el barrido: {}", notificationId, e.getMessage());
        }
    }

    public Optional<Long> poll() {
        try {
            String v = redis.opsForList().rightPop(key);
            return v == null ? Optional.empty() : Optional.of(Long.parseLong(v));
        } catch (RuntimeException e) {
            log.debug("Redis no disponible al leer la cola: {}", e.getMessage());
            return Optional.empty();
        }
    }
}
