package com.esfx.taskflow.notifications;

import com.esfx.taskflow.notifications.NotificationRepository.Row;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Optional;

/** Toma correos de la cola de Redis y los entrega al proveedor (Gmail/SMTP), con reintentos. */
@Component
public class NotificationWorker {
    private static final Logger log = LoggerFactory.getLogger(NotificationWorker.class);

    private final NotificationQueue queue;
    private final NotificationRepository repo;
    private final EmailProvider provider;
    private final int maxAttempts;

    public NotificationWorker(NotificationQueue queue, NotificationRepository repo, EmailProvider provider,
                              @Value("${app.notifications.max-attempts}") int maxAttempts) {
        this.queue = queue;
        this.repo = repo;
        this.provider = provider;
        this.maxAttempts = maxAttempts;
    }

    @Scheduled(fixedDelayString = "${app.notifications.worker-delay-ms}")
    public void drainQueue() {
        for (int i = 0; i < 50; i++) {
            Optional<Long> id = queue.poll();
            if (id.isEmpty()) return;
            process(id.get());
        }
    }

    /** Barrido: reenvía lo que quedó PENDING sin pasar por la cola (Redis caído, reintentos, reinicios). */
    @Scheduled(fixedDelayString = "${app.notifications.sweep-delay-ms}", initialDelayString = "${app.notifications.sweep-delay-ms}")
    public void sweep() {
        for (Long id : repo.stalePendingEmails(120, 100)) process(id);
    }

    void process(long id) {
        Optional<Row> found = repo.findById(id);
        if (found.isEmpty()) return;
        Row n = found.get();
        if (!"PENDING".equals(n.status()) || !"EMAIL".equals(n.channel())) return; // ya procesada
        try {
            provider.send(n.recipientEmail(), n.title(), n.body());
            repo.markSent(id);
        } catch (Exception e) {
            log.warn("Falló el envío de la notificación {}: {}", id, e.getMessage());
            repo.markAttemptFailed(id, e.getMessage(), maxAttempts);
        }
    }
}
