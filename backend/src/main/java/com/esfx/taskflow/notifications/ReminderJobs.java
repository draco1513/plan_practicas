package com.esfx.taskflow.notifications;

import com.esfx.taskflow.common.CalendarApi;
import com.esfx.taskflow.common.CalendarApi.UpcomingMeeting;
import com.esfx.taskflow.common.Dates;
import com.esfx.taskflow.common.ProjectsApi;
import com.esfx.taskflow.common.ProjectsApi.TaskView;
import com.esfx.taskflow.notifications.NotificationDtos.PreferencesDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import static com.esfx.taskflow.notifications.NotificationEventListeners.DATE;
import static com.esfx.taskflow.notifications.NotificationEventListeners.DATE_TIME;

/**
 * Tareas programadas: recordatorios de reuniones, tareas por vencer y tareas vencidas.
 * Cada aviso usa una clave de idempotencia, así que aunque el job corra cada minuto no se repite.
 */
@Component
public class ReminderJobs {
    private static final Logger log = LoggerFactory.getLogger(ReminderJobs.class);

    private final NotificationService service;
    private final NotificationRepository repo;
    private final ProjectsApi projects;
    private final CalendarApi calendar;

    public ReminderJobs(NotificationService service, NotificationRepository repo, ProjectsApi projects, CalendarApi calendar) {
        this.service = service;
        this.repo = repo;
        this.projects = projects;
        this.calendar = calendar;
    }

    @Scheduled(fixedDelayString = "${app.notifications.reminder-delay-ms}", initialDelayString = "${app.notifications.reminder-delay-ms}")
    public void run() {
        try { meetingReminders(); } catch (RuntimeException e) { log.error("Falló el job de recordatorios de reuniones", e); }
        try { taskReminders(); } catch (RuntimeException e) { log.error("Falló el job de recordatorios de tareas", e); }
    }

    void meetingReminders() {
        Instant now = Instant.now();
        for (UpcomingMeeting m : calendar.meetingsStartingBetween(now, now.plus(Duration.ofHours(24)))) {
            for (Long userId : m.participantIds()) {
                PreferencesDto prefs = repo.preferences(userId);
                if (prefs.meetingReminderMin() == 0) continue;
                if (!now.isBefore(m.startAt().minus(Duration.ofMinutes(prefs.meetingReminderMin())))) {
                    Map<String, String> vars = new HashMap<>();
                    vars.put("meetingTitle", m.title());
                    vars.put("startAt", DATE_TIME.format(m.startAt()));
                    service.notifyUser(userId, "MEETING_REMINDER", vars, Map.of("meetingId", m.id()),
                            "MEETING_REMINDER:" + m.id() + ":" + userId + ":" + m.startAt().getEpochSecond());
                }
            }
        }
    }

    void taskReminders() {
        Instant now = Instant.now();
        LocalDate today = LocalDate.now(Dates.ZONE);
        // Con el mayor margen posible (30 días de recordatorio) se traen las candidatas y se filtra por usuario
        for (TaskView t : projects.assignedTasksDueUntil(today.plusDays(30))) {
            if (t.assignee() == null || t.dueDate() == null) continue;
            long userId = t.assignee().id();
            Instant dueEnd = t.dueDate().plusDays(1).atStartOfDay(Dates.ZONE).toInstant(); // vence al terminar ese día
            Map<String, String> vars = new HashMap<>();
            vars.put("taskKey", t.key());
            vars.put("taskTitle", t.title());
            vars.put("dueDate", t.dueDate().format(DATE));
            Map<String, Object> payload = Map.of("taskId", t.id(), "projectId", t.projectId());
            if (!dueEnd.isAfter(now)) {
                service.notifyUser(userId, "TASK_OVERDUE", vars, payload, "TASK_OVERDUE:" + t.id() + ":" + userId + ":" + t.dueDate());
            } else {
                PreferencesDto prefs = repo.preferences(userId);
                if (prefs.taskDueReminderHrs() > 0 && !now.isBefore(dueEnd.minus(Duration.ofHours(prefs.taskDueReminderHrs())))) {
                    service.notifyUser(userId, "TASK_DUE_SOON", vars, payload, "TASK_DUE_SOON:" + t.id() + ":" + userId + ":" + t.dueDate());
                }
            }
        }
    }
}
