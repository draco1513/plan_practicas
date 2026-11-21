package com.esfx.taskflow.notifications;

import com.esfx.taskflow.common.Dates;
import com.esfx.taskflow.common.events.DomainEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/** Convierte los eventos de los otros módulos en notificaciones (se ejecuta después de confirmar la operación original). */
@Component
public class NotificationEventListeners {
    private static final Logger log = LoggerFactory.getLogger(NotificationEventListeners.class);
    static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(Dates.ZONE);
    static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final NotificationService service;
    private final EmailProvider provider;

    public NotificationEventListeners(NotificationService service, EmailProvider provider) {
        this.service = service;
        this.provider = provider;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onTaskAssigned(DomainEvents.TaskAssigned e) {
        try {
            Map<String, String> vars = new HashMap<>();
            vars.put("taskKey", e.taskKey());
            vars.put("taskTitle", e.taskTitle());
            vars.put("projectName", e.projectName());
            vars.put("dueDate", e.dueDate() == null ? "sin fecha" : e.dueDate().format(DATE));
            Map<String, Object> payload = Map.of("taskId", e.taskId(), "projectId", e.projectId());
            service.notifyUser(e.assigneeId(), "TASK_ASSIGNED", vars, payload,
                    "TASK_ASSIGNED:" + e.taskId() + ":" + e.assigneeId() + ":" + System.currentTimeMillis());
        } catch (RuntimeException ex) {
            log.error("No se pudo crear la notificación de tarea asignada", ex);
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onMeetingInvited(DomainEvents.MeetingInvited e) {
        for (Long userId : e.inviteeIds()) {
            try {
                Map<String, String> vars = new HashMap<>();
                vars.put("meetingTitle", e.title());
                vars.put("startAt", DATE_TIME.format(e.startAt()));
                vars.put("location", e.location() == null ? "por definir" : e.location());
                Map<String, Object> payload = Map.of("meetingId", e.meetingId());
                service.notifyUser(userId, "MEETING_INVITATION", vars, payload, "MEETING_INVITATION:" + e.meetingId() + ":" + userId);
            } catch (RuntimeException ex) {
                log.error("No se pudo crear la invitación para el usuario {}", userId, ex);
            }
        }
    }

    /** El enlace de recuperación se envía directo y no se guarda en la tabla de notificaciones. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onPasswordReset(DomainEvents.PasswordResetRequested e) {
        try {
            Map<String, String> vars = Map.of("userName", e.fullName(), "link", e.resetLink());
            service.renderEmail("PASSWORD_RESET", vars).ifPresent(r -> {
                try {
                    provider.send(e.email(), r[0], r[1]);
                } catch (Exception ex) {
                    log.warn("No se pudo enviar el correo de recuperación: {}", ex.getMessage());
                }
            });
        } catch (RuntimeException ex) {
            log.error("Error al preparar el correo de recuperación", ex);
        }
    }

}
