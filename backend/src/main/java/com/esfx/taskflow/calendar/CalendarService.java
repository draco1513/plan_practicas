package com.esfx.taskflow.calendar;

import com.esfx.taskflow.calendar.CalendarDtos.*;
import com.esfx.taskflow.common.*;
import com.esfx.taskflow.common.ProjectsApi.TaskView;
import com.esfx.taskflow.common.events.DomainEvents;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;

@Service
public class CalendarService {
    private final MeetingRepository meetings;
    private final UserDirectory directory;
    private final ProjectsApi projectsApi;
    private final AuditPort audit;
    private final ApplicationEventPublisher events;

    public CalendarService(MeetingRepository meetings, UserDirectory directory, ProjectsApi projectsApi, AuditPort audit,
                           ApplicationEventPublisher events) {
        this.meetings = meetings;
        this.directory = directory;
        this.projectsApi = projectsApi;
        this.audit = audit;
        this.events = events;
    }

    public List<CalendarEvent> calendar(Instant from, Instant to, String scope) {
        if (from == null || to == null || !to.isAfter(from)) throw ApiException.badRequest("Rango de fechas no válido");
        if (java.time.Duration.between(from, to).toDays() > 62) throw ApiException.badRequest("El rango máximo es de 62 días");
        long me = AuthUser.currentId();
        boolean team = "team".equalsIgnoreCase(scope);
        boolean viewAll = AuthUser.can("REPORT_VIEW_ALL") || AuthUser.can("USER_MANAGE");
        List<Long> projectIds = team ? projectsApi.visibleProjectIds() : List.of();

        String mode = !team ? "ME" : (viewAll ? "ALL" : "PROJECTS");
        List<CalendarEvent> out = new ArrayList<>();
        for (MeetingRow m : meetings.inRange(from, to, mode, me, projectIds)) {
            out.add(new CalendarEvent("MEETING", m.id(), m.title(), m.startAt(), m.endAt(), false));
        }

        LocalDate d1 = from.atZone(Dates.ZONE).toLocalDate();
        LocalDate d2 = to.atZone(Dates.ZONE).toLocalDate();
        List<TaskView> due = team
                ? projectsApi.tasksDueBetween(projectIds, d1, d2, null)
                : projectsApi.tasksDueBetween(projectsApi.visibleProjectIds(), d1, d2, me);
        for (TaskView t : due) {
            Instant at = t.dueDate().atTime(LocalTime.NOON).atZone(Dates.ZONE).toInstant();
            out.add(new CalendarEvent("TASK_DUE", t.id(), t.key() + " · " + t.title(), at, null, true));
        }
        out.sort(Comparator.comparing(CalendarEvent::startAt));
        return out;
    }

    public MeetingDto get(long id) {
        MeetingRow m = meetings.findById(id).orElseThrow(() -> ApiException.notFound("Reunión"));
        boolean allowed = AuthUser.can("REPORT_VIEW_ALL") || AuthUser.can("USER_MANAGE")
                || meetings.isParticipant(id, AuthUser.currentId())
                || (m.projectId() != null && projectsApi.canView(m.projectId()));
        if (!allowed) throw ApiException.notFound("Reunión");
        return toDto(m);
    }

    @Transactional
    public MeetingDto create(MeetingCreate req) {
        validate(req);
        long me = AuthUser.currentId();
        if (req.projectId() != null && !projectsApi.canView(req.projectId())) throw ApiException.badRequest("Proyecto no válido");
        Set<Long> invitees = cleanInvitees(req.inviteeIds(), me);
        long id = meetings.insert(req.title().trim(), req.startAt(), req.endAt(), blankToNull(req.location()), me, req.projectId());
        for (Long u : invitees) meetings.addInvitee(id, u);
        audit.record(me, "CREATE", "MEETING", id, Map.of("title", req.title().trim()));
        if (!invitees.isEmpty()) {
            events.publishEvent(new DomainEvents.MeetingInvited(id, req.title().trim(), req.startAt(), blankToNull(req.location()), me, List.copyOf(invitees)));
        }
        return toDto(meetings.findById(id).orElseThrow());
    }

    @Transactional
    public MeetingDto update(long id, MeetingCreate req) {
        MeetingRow m = requireOrganizer(id);
        validate(req);
        if (req.projectId() != null && !projectsApi.canView(req.projectId())) throw ApiException.badRequest("Proyecto no válido");
        Set<Long> wanted = cleanInvitees(req.inviteeIds(), m.organizerId());
        Set<Long> current = meetings.invitees(id).keySet();
        List<Long> added = new ArrayList<>();
        for (Long u : wanted) if (!current.contains(u)) { meetings.addInvitee(id, u); added.add(u); }
        for (Long u : new ArrayList<>(current)) if (!wanted.contains(u)) meetings.removeInvitee(id, u);
        meetings.update(id, req.title().trim(), req.startAt(), req.endAt(), blankToNull(req.location()), req.projectId());
        audit.record(AuthUser.currentId(), "UPDATE", "MEETING", id, Map.of("title", req.title().trim()));
        if (!added.isEmpty()) {
            events.publishEvent(new DomainEvents.MeetingInvited(id, req.title().trim(), req.startAt(), blankToNull(req.location()), m.organizerId(), added));
        }
        return toDto(meetings.findById(id).orElseThrow());
    }

    @Transactional
    public void cancel(long id) {
        MeetingRow m = requireOrganizer(id);
        meetings.delete(id);
        audit.record(AuthUser.currentId(), "DELETE", "MEETING", id, Map.of("title", m.title()));
    }

    @Transactional
    public MeetingDto respond(long id, String response) {
        meetings.findById(id).orElseThrow(() -> ApiException.notFound("Reunión"));
        if (!meetings.invitees(id).containsKey(AuthUser.currentId())) {
            throw ApiException.forbidden("No estás invitado a esta reunión");
        }
        meetings.respond(id, AuthUser.currentId(), response);
        return toDto(meetings.findById(id).orElseThrow());
    }

    private MeetingRow requireOrganizer(long id) {
        MeetingRow m = meetings.findById(id).orElseThrow(() -> ApiException.notFound("Reunión"));
        if (m.organizerId() != AuthUser.currentId() && !AuthUser.can("USER_MANAGE")) {
            throw ApiException.forbidden("Solo quien organiza la reunión puede modificarla");
        }
        return m;
    }

    private void validate(MeetingCreate req) {
        if (!req.endAt().isAfter(req.startAt())) throw ApiException.badRequest("La hora de fin debe ser posterior a la de inicio");
    }

    private Set<Long> cleanInvitees(List<Long> ids, long organizerId) {
        Set<Long> set = new LinkedHashSet<>(ids);
        set.remove(organizerId);
        for (Long u : set) {
            if (!directory.existsActive(u)) throw ApiException.badRequest("El usuario " + u + " no existe o está inactivo");
        }
        return set;
    }

    private static String blankToNull(String s) { return s == null || s.isBlank() ? null : s.trim(); }

    private MeetingDto toDto(MeetingRow m) {
        Map<Long, String> inv = meetings.invitees(m.id());
        Set<Long> ids = new HashSet<>(inv.keySet());
        ids.add(m.organizerId());
        Map<Long, UserSummary> users = directory.findAll(ids);
        List<InviteeDto> invitees = new ArrayList<>();
        for (Map.Entry<Long, String> e : inv.entrySet()) {
            invitees.add(new InviteeDto(users.getOrDefault(e.getKey(), new UserSummary(e.getKey(), "Usuario " + e.getKey(), "")), e.getValue()));
        }
        UserSummary organizer = users.getOrDefault(m.organizerId(), new UserSummary(m.organizerId(), "Usuario " + m.organizerId(), ""));
        return new MeetingDto(m.id(), m.title(), m.startAt(), m.endAt(), m.location(), organizer, m.projectId(), invitees);
    }
}
