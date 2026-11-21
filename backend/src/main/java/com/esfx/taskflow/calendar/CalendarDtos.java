package com.esfx.taskflow.calendar;

import com.esfx.taskflow.common.UserSummary;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

public final class CalendarDtos {
    private CalendarDtos() {}

    public record CalendarEvent(String type, long refId, String title, Instant startAt, Instant endAt, boolean allDay) {}

    public record InviteeDto(UserSummary user, String response) {}

    public record MeetingDto(long id, String title, Instant startAt, Instant endAt, String location, UserSummary organizer,
                             Long projectId, List<InviteeDto> invitees) {}

    public record MeetingCreate(@NotBlank @Size(max = 150) String title, @NotNull Instant startAt, @NotNull Instant endAt,
                                @Size(max = 200) String location, Long projectId, @NotNull List<Long> inviteeIds) {}

    public record ResponseRequest(@NotBlank @Pattern(regexp = "ACCEPTED|DECLINED", message = "Use ACCEPTED o DECLINED") String response) {}
}
