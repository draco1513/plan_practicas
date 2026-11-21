package com.esfx.taskflow.calendar;

import com.esfx.taskflow.calendar.CalendarDtos.*;
import com.esfx.taskflow.common.Dates;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
public class CalendarController {
    private final CalendarService service;

    public CalendarController(CalendarService service) { this.service = service; }

    @GetMapping("/calendar")
    public List<CalendarEvent> calendar(@RequestParam String from, @RequestParam String to,
                                        @RequestParam(defaultValue = "me") String scope) {
        return service.calendar(Dates.parse(from, false), Dates.parse(to, true), scope);
    }

    @PostMapping("/meetings")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('MEETING_CREATE')")
    public MeetingDto create(@Valid @RequestBody MeetingCreate req) { return service.create(req); }

    @GetMapping("/meetings/{id}")
    public MeetingDto get(@PathVariable long id) { return service.get(id); }

    @PutMapping("/meetings/{id}")
    @PreAuthorize("hasAuthority('MEETING_CREATE')")
    public MeetingDto update(@PathVariable long id, @Valid @RequestBody MeetingCreate req) { return service.update(id, req); }

    @DeleteMapping("/meetings/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('MEETING_CREATE')")
    public void cancel(@PathVariable long id) { service.cancel(id); }

    @PutMapping("/meetings/{id}/response")
    @PreAuthorize("hasAuthority('MEETING_RESPOND')")
    public MeetingDto respond(@PathVariable long id, @Valid @RequestBody ResponseRequest req) {
        return service.respond(id, req.response());
    }
}
