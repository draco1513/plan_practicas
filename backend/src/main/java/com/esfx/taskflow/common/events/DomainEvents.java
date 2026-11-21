package com.esfx.taskflow.common.events;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Eventos que los módulos publican para avisarse entre sí sin conocerse
 * (hoy se entregan con Spring en memoria; mañana podrían ir por una cola).
 */
public final class DomainEvents {
    private DomainEvents() {}

    public record PasswordResetRequested(long userId, String email, String fullName, String resetLink) {}

    public record TaskAssigned(long taskId, long projectId, String taskKey, String taskTitle, String projectName,
                               LocalDate dueDate, long assigneeId, long assignedBy) {}

    public record MeetingInvited(long meetingId, String title, Instant startAt, String location,
                                 long organizerId, List<Long> inviteeIds) {}
}
