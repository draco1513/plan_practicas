package com.esfx.taskflow.common;

import java.time.Instant;
import java.util.List;

/** API pública del módulo "calendar" (la usa notificaciones para los recordatorios de reuniones). */
public interface CalendarApi {
    /** Reunión próxima con las personas que participan (organizador e invitados que no la rechazaron). */
    record UpcomingMeeting(long id, String title, Instant startAt, String location, List<Long> participantIds) {}

    List<UpcomingMeeting> meetingsStartingBetween(Instant from, Instant to);
}
