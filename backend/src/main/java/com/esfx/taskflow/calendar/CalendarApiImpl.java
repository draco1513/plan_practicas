package com.esfx.taskflow.calendar;

import com.esfx.taskflow.common.CalendarApi;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

@Component
public class CalendarApiImpl implements CalendarApi {
    private final MeetingRepository meetings;

    public CalendarApiImpl(MeetingRepository meetings) { this.meetings = meetings; }

    @Override
    public List<UpcomingMeeting> meetingsStartingBetween(Instant from, Instant to) {
        return meetings.startingBetween(from, to).stream()
                .map(m -> new UpcomingMeeting(m.id(), m.title(), m.startAt(), m.location(),
                        meetings.participantsNotDeclined(m.id(), m.organizerId())))
                .toList();
    }
}
