package com.esfx.taskflow.calendar;

import java.time.Instant;

public record MeetingRow(long id, String title, Instant startAt, Instant endAt, String location, long organizerId, Long projectId) {}
