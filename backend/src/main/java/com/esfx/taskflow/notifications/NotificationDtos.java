package com.esfx.taskflow.notifications;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.Map;

public final class NotificationDtos {
    private NotificationDtos() {}

    public record NotificationDto(long id, String type, String title, String body, Map<String, Object> payload,
                                  boolean read, Instant createdAt) {}

    public record CountDto(long count) {}

    public record PreferencesDto(@NotNull Boolean emailEnabled,
                                 @NotNull @Min(0) @Max(10080) Integer meetingReminderMin,
                                 @NotNull @Min(0) @Max(720) Integer taskDueReminderHrs) {}
}
