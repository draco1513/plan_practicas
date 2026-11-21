package com.esfx.taskflow.timetracking;

import com.esfx.taskflow.common.*;
import com.esfx.taskflow.common.ProjectsApi.TaskView;
import com.esfx.taskflow.timetracking.TimeDtos.*;
import com.esfx.taskflow.timetracking.TimeRepository.Row;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.*;

@Service
public class TimeService {
    private final TimeRepository repo;
    private final ProjectsApi projects;
    private final UserDirectory directory;
    private final AuditPort audit;

    public TimeService(TimeRepository repo, ProjectsApi projects, UserDirectory directory, AuditPort audit) {
        this.repo = repo;
        this.projects = projects;
        this.directory = directory;
        this.audit = audit;
    }

    public PageResponse<TimeEntryDto> list(Long userId, Long projectId, Long taskId, LocalDate from, LocalDate to, Paging paging) {
        List<Long> visible = projects.visibleProjectIds();
        if (visible.isEmpty()) return PageResponse.of(List.of(), paging.page(), paging.size(), 0);
        List<Row> rows = repo.search(visible, userId, projectId, taskId, from, to, paging.size(), paging.offset());
        return PageResponse.of(toDtos(rows), paging.page(), paging.size(), repo.count(visible, userId, projectId, taskId, from, to));
    }

    @Transactional
    public TimeEntryDto create(TimeEntryCreate req) {
        TaskView task = visibleTask(req.taskId());
        checkDate(req.workDate());
        long id = repo.insert(task.id(), task.projectId(), AuthUser.currentId(), req.workDate(), req.hours(), clean(req.note()));
        audit.record(AuthUser.currentId(), "CREATE", "TIME_ENTRY", id, Map.of("taskKey", task.key(), "hours", req.hours()));
        return toDtos(List.of(repo.findById(id).orElseThrow())).get(0);
    }

    @Transactional
    public TimeEntryDto update(long id, TimeEntryCreate req) {
        Row r = ownEntry(id);
        TaskView task = visibleTask(req.taskId());
        checkDate(req.workDate());
        repo.update(id, task.id(), task.projectId(), req.workDate(), req.hours(), clean(req.note()));
        audit.record(AuthUser.currentId(), "UPDATE", "TIME_ENTRY", id, Map.of("hours", Map.of("from", r.hours(), "to", req.hours())));
        return toDtos(List.of(repo.findById(id).orElseThrow())).get(0);
    }

    @Transactional
    public void delete(long id) {
        ownEntry(id);
        repo.delete(id);
        audit.record(AuthUser.currentId(), "DELETE", "TIME_ENTRY", id, null);
    }

    public TimeSummary summary(long projectId) {
        if (!projects.canView(projectId)) throw ApiException.notFound("Proyecto");
        List<TimeRepository.UserTotal> users = repo.byUser(projectId);
        List<TimeRepository.TaskTotal> tasks = repo.byTask(projectId);
        Map<Long, UserSummary> userMap = directory.findAll(users.stream().map(TimeRepository.UserTotal::userId).toList());
        Map<Long, TaskView> taskMap = projects.tasks(tasks.stream().map(TimeRepository.TaskTotal::taskId).toList());
        List<UserHours> byUser = users.stream()
                .map(u -> new UserHours(userMap.getOrDefault(u.userId(), new UserSummary(u.userId(), "Usuario " + u.userId(), "")), u.hours()))
                .toList();
        List<TaskHours> byTask = new ArrayList<>();
        for (TimeRepository.TaskTotal t : tasks) {
            TaskView v = taskMap.get(t.taskId());
            byTask.add(new TaskHours(v == null ? "#" + t.taskId() : v.key(), v == null ? "(tarea eliminada)" : v.title(), t.hours()));
        }
        return new TimeSummary(projectId, repo.total(projectId), byUser, byTask);
    }

    private TaskView visibleTask(long taskId) {
        return projects.task(taskId).orElseThrow(() -> ApiException.notFound("Tarea"));
    }

    /** Solo la persona que registró las horas puede cambiarlas o borrarlas. */
    private Row ownEntry(long id) {
        Row r = repo.findById(id).orElseThrow(() -> ApiException.notFound("Registro de horas"));
        if (r.userId() != AuthUser.currentId()) throw ApiException.forbidden("Solo puedes modificar tus propias horas");
        return r;
    }

    private void checkDate(LocalDate d) {
        if (d.isAfter(LocalDate.now(Dates.ZONE))) throw ApiException.badRequest("No se pueden registrar horas en una fecha futura");
    }

    private static String clean(String s) { return s == null || s.isBlank() ? null : s.trim(); }

    private List<TimeEntryDto> toDtos(List<Row> rows) {
        Map<Long, UserSummary> users = directory.findAll(rows.stream().map(Row::userId).toList());
        Map<Long, TaskView> tasks = projects.tasks(rows.stream().map(Row::taskId).toList());
        List<TimeEntryDto> out = new ArrayList<>();
        for (Row r : rows) {
            TaskView t = tasks.get(r.taskId());
            out.add(new TimeEntryDto(r.id(), r.taskId(), t == null ? "#" + r.taskId() : t.key(), r.projectId(),
                    users.getOrDefault(r.userId(), new UserSummary(r.userId(), "Usuario " + r.userId(), "")),
                    r.workDate(), r.hours(), r.note()));
        }
        return out;
    }

    @RestController
    @RequestMapping("/api")
    public static class Controller {
        private final TimeService service;

        public Controller(TimeService service) { this.service = service; }

        @GetMapping("/time-entries")
        public PageResponse<TimeEntryDto> list(@RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size,
                                               @RequestParam(required = false) Long userId, @RequestParam(required = false) Long projectId,
                                               @RequestParam(required = false) Long taskId, @RequestParam(required = false) String from,
                                               @RequestParam(required = false) String to) {
            return service.list(userId, projectId, taskId, day(from), day(to), Paging.of(page, size));
        }

        @PostMapping("/time-entries")
        @ResponseStatus(HttpStatus.CREATED)
        @PreAuthorize("hasAuthority('TIME_LOG')")
        public TimeEntryDto create(@Valid @RequestBody TimeEntryCreate req) { return service.create(req); }

        @PutMapping("/time-entries/{id}")
        @PreAuthorize("hasAuthority('TIME_LOG')")
        public TimeEntryDto update(@PathVariable long id, @Valid @RequestBody TimeEntryCreate req) { return service.update(id, req); }

        @DeleteMapping("/time-entries/{id}")
        @ResponseStatus(HttpStatus.NO_CONTENT)
        @PreAuthorize("hasAuthority('TIME_LOG')")
        public void delete(@PathVariable long id) { service.delete(id); }

        @GetMapping("/projects/{projectId}/time-summary")
        public TimeSummary summary(@PathVariable long projectId) { return service.summary(projectId); }

        private static LocalDate day(String s) {
            if (s == null || s.isBlank()) return null;
            try {
                return LocalDate.parse(s.substring(0, Math.min(10, s.length())));
            } catch (java.time.format.DateTimeParseException e) {
                throw ApiException.badRequest("Fecha no válida: " + s);
            }
        }
    }
}
