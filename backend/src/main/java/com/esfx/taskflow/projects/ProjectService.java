package com.esfx.taskflow.projects;

import com.esfx.taskflow.common.*;
import com.esfx.taskflow.projects.ProjectsDtos.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;

@Service
public class ProjectService {
    private final ProjectRepository projects;
    private final UserDirectory directory;
    private final AuditPort audit;

    public ProjectService(ProjectRepository projects, UserDirectory directory, AuditPort audit) {
        this.projects = projects;
        this.directory = directory;
        this.audit = audit;
    }

    /** null = ve todos los proyectos; si no, solo los propios (miembro o responsable). */
    static Long visibilityFilter() {
        return AuthUser.can("REPORT_VIEW_ALL") || AuthUser.can("USER_MANAGE") ? null : AuthUser.currentId();
    }

    /** Devuelve el proyecto si el usuario actual puede verlo; si no, responde 404 (no revela que existe). */
    ProjectRow requireVisible(long projectId) {
        ProjectRow p = projects.findById(projectId).orElseThrow(() -> ApiException.notFound("Proyecto"));
        Long filter = visibilityFilter();
        if (filter != null && !projects.isVisibleTo(projectId, filter)) throw ApiException.notFound("Proyecto");
        return p;
    }

    public PageResponse<ProjectDto> list(String status, Paging paging) {
        Long filter = visibilityFilter();
        List<ProjectRow> rows = projects.search(filter, status, paging.size(), paging.offset());
        Map<Long, UserSummary> owners = directory.findAll(rows.stream().map(ProjectRow::ownerId).toList());
        return PageResponse.of(rows.stream().map(r -> toDto(r, owners)).toList(), paging.page(), paging.size(), projects.count(filter, status));
    }

    public ProjectDto get(long id) {
        ProjectRow r = requireVisible(id);
        return toDto(r, directory.findAll(List.of(r.ownerId())));
    }

    @Transactional
    public ProjectDto create(ProjectCreate req) {
        String code = req.code().trim().toUpperCase();
        if (projects.codeExists(code)) throw ApiException.conflict("Ya existe un proyecto con el código " + code);
        requireActiveUser(req.ownerId(), "El responsable");
        checkDates(req.startDate(), req.endDate());
        long id = projects.insert(code, req.name().trim(), req.ownerId(), req.startDate(), req.endDate());
        projects.addMember(id, req.ownerId());
        audit.record(AuthUser.currentId(), "CREATE", "PROJECT", id, Map.of("code", code, "name", req.name().trim()));
        return toDto(projects.findById(id).orElseThrow(), directory.findAll(List.of(req.ownerId())));
    }

    @Transactional
    public ProjectDto update(long id, ProjectUpdate req) {
        ProjectRow before = requireVisible(id);
        requireActiveUser(req.ownerId(), "El responsable");
        checkDates(req.startDate(), req.endDate());
        projects.update(id, req.name().trim(), req.ownerId(), req.status(), req.startDate(), req.endDate());
        projects.addMember(id, req.ownerId());
        audit.record(AuthUser.currentId(), "UPDATE", "PROJECT", id, Map.of(
                "status", Map.of("from", before.status(), "to", req.status()),
                "name", Map.of("from", before.name(), "to", req.name().trim())));
        return toDto(projects.findById(id).orElseThrow(), directory.findAll(List.of(req.ownerId())));
    }

    public List<UserSummary> members(long id) {
        requireVisible(id);
        Map<Long, UserSummary> map = directory.findAll(projects.memberIds(id));
        return projects.memberIds(id).stream().map(map::get).filter(Objects::nonNull).toList();
    }

    @Transactional
    public List<UserSummary> setMembers(long id, List<Long> userIds) {
        ProjectRow p = requireVisible(id);
        Set<Long> ids = new LinkedHashSet<>(userIds);
        ids.add(p.ownerId()); // el responsable siempre es miembro
        for (Long uid : ids) requireActiveUser(uid, "El usuario " + uid);
        projects.replaceMembers(id, ids);
        audit.record(AuthUser.currentId(), "UPDATE_MEMBERS", "PROJECT", id, Map.of("userIds", ids));
        return members(id);
    }

    private void requireActiveUser(long userId, String label) {
        if (!directory.existsActive(userId)) throw ApiException.badRequest(label + " no existe o está inactivo");
    }

    private void checkDates(LocalDate start, LocalDate end) {
        if (start != null && end != null && end.isBefore(start)) {
            throw ApiException.badRequest("La fecha de fin no puede ser anterior a la de inicio");
        }
    }

    private ProjectDto toDto(ProjectRow r, Map<Long, UserSummary> owners) {
        UserSummary owner = owners.getOrDefault(r.ownerId(), new UserSummary(r.ownerId(), "Usuario " + r.ownerId(), ""));
        return new ProjectDto(r.id(), r.code(), r.name(), owner, r.status(), r.startDate(), r.endDate(), r.progress(), r.taskCount());
    }
}
