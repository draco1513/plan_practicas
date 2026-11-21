package com.esfx.taskflow.projects;

import com.esfx.taskflow.common.*;
import com.esfx.taskflow.common.ProjectsApi.TaskView;
import com.esfx.taskflow.common.events.DomainEvents;
import com.esfx.taskflow.projects.ProjectsDtos.*;
import com.esfx.taskflow.projects.TaskRepository.AttachmentRow;
import com.esfx.taskflow.projects.TaskRepository.CommentRow;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.*;

@Service
public class TaskService {
    static final List<String> COLUMNS = List.of("TODO", "IN_PROGRESS", "DONE");

    private final TaskRepository tasks;
    private final ProjectRepository projects;
    private final ProjectService projectService;
    private final UserDirectory directory;
    private final AuditPort audit;
    private final ApplicationEventPublisher events;
    private final AttachmentStorage storage;

    public TaskService(TaskRepository tasks, ProjectRepository projects, ProjectService projectService,
                       UserDirectory directory, AuditPort audit, ApplicationEventPublisher events, AttachmentStorage storage) {
        this.tasks = tasks;
        this.projects = projects;
        this.projectService = projectService;
        this.directory = directory;
        this.audit = audit;
        this.events = events;
        this.storage = storage;
    }

    // ------------------------------------------------------------------ lectura

    public PageResponse<TaskView> list(long projectId, String status, Long assigneeId, boolean overdue, Paging paging) {
        projectService.requireVisible(projectId);
        LocalDate today = LocalDate.now(Dates.ZONE);
        List<TaskRow> rows = tasks.search(projectId, status, assigneeId, overdue, today, paging.size(), paging.offset());
        return PageResponse.of(toViews(rows), paging.page(), paging.size(), tasks.count(projectId, status, assigneeId, overdue, today));
    }

    public BoardDto board(long projectId) {
        projectService.requireVisible(projectId);
        List<TaskView> all = toViews(tasks.board(projectId));
        List<BoardColumn> columns = new ArrayList<>();
        for (String s : COLUMNS) {
            columns.add(new BoardColumn(s, all.stream().filter(t -> t.status().equals(s)).toList()));
        }
        return new BoardDto(projectId, columns);
    }

    public TaskView get(long taskId) {
        TaskRow t = requireVisibleTask(taskId);
        return toViews(List.of(t)).get(0);
    }

    TaskRow requireVisibleTask(long taskId) {
        TaskRow t = tasks.findById(taskId).orElseThrow(() -> ApiException.notFound("Tarea"));
        projectService.requireVisible(t.projectId());
        return t;
    }

    // ------------------------------------------------------------------ escritura

    @Transactional
    public TaskView create(long projectId, TaskCreate req) {
        ProjectRow project = projectService.requireVisible(projectId);
        long me = AuthUser.currentId();
        if (req.assigneeId() != null && !AuthUser.can("TASK_ASSIGN")) {
            throw ApiException.forbidden("No tienes permiso para asignar tareas");
        }
        if (req.assigneeId() != null && !directory.existsActive(req.assigneeId())) {
            throw ApiException.badRequest("El responsable no existe o está inactivo");
        }
        int number = tasks.nextTaskNumber(projectId);
        int position = tasks.maxPosition(projectId, "TODO") + 1;
        long id = tasks.insert(projectId, number, req.title().trim(), req.priority() == null ? "MEDIUM" : req.priority(),
                req.assigneeId(), me, req.dueDate(), position);
        if (req.assigneeId() != null) projects.addMember(projectId, req.assigneeId());
        TaskRow created = tasks.findById(id).orElseThrow();
        audit.record(me, "CREATE", "TASK", id, Map.of("key", created.key(), "title", created.title()));
        if (req.assigneeId() != null) publishAssigned(created, project.name(), req.assigneeId(), me);
        return toViews(List.of(created)).get(0);
    }

    @Transactional
    public TaskView update(long taskId, TaskUpdate req) {
        TaskRow t = requireVisibleTask(taskId);
        requireCanEdit(t);
        tasks.update(taskId, req.title().trim(), req.priority(), req.dueDate());
        audit.record(AuthUser.currentId(), "UPDATE", "TASK", taskId, Map.of(
                "title", Map.of("from", t.title(), "to", req.title().trim()),
                "priority", Map.of("from", t.priority(), "to", req.priority())));
        return get(taskId);
    }

    @Transactional
    public TaskView assign(long taskId, Long assigneeId) {
        TaskRow t = requireVisibleTask(taskId);
        if (assigneeId != null && !directory.existsActive(assigneeId)) {
            throw ApiException.badRequest("El responsable no existe o está inactivo");
        }
        if (Objects.equals(t.assigneeId(), assigneeId)) return get(taskId);
        tasks.setAssignee(taskId, assigneeId);
        if (assigneeId != null) projects.addMember(t.projectId(), assigneeId);
        Map<String, Object> detail = new HashMap<>();
        detail.put("from", t.assigneeId());
        detail.put("to", assigneeId);
        audit.record(AuthUser.currentId(), "ASSIGN", "TASK", taskId, detail);
        if (assigneeId != null) {
            publishAssigned(t, projects.findById(t.projectId()).map(ProjectRow::name).orElse(""), assigneeId, AuthUser.currentId());
        }
        return get(taskId);
    }

    /** Mueve la tarea de columna y/o de posición (tablero Kanban). */
    @Transactional
    public TaskView move(long taskId, String status, int position) {
        TaskRow t = requireVisibleTask(taskId);
        requireCanEdit(t);
        List<Long> column = new ArrayList<>(tasks.columnIds(t.projectId(), status, taskId));
        int index = Math.max(0, Math.min(position, column.size()));
        column.add(index, taskId);
        for (int i = 0; i < column.size(); i++) tasks.setPosition(column.get(i), i);
        if (!status.equals(t.status())) {
            int progress = status.equals("DONE") ? 100 : (status.equals("TODO") ? 0 : Math.min(t.progress(), 99));
            tasks.setStatusProgress(taskId, status, progress);
            audit.record(AuthUser.currentId(), "MOVE", "TASK", taskId, Map.of("from", t.status(), "to", status));
        }
        return get(taskId);
    }

    @Transactional
    public TaskView setProgress(long taskId, int progress) {
        TaskRow t = requireVisibleTask(taskId);
        requireCanEdit(t);
        String status = progress >= 100 ? "DONE" : (progress > 0 ? "IN_PROGRESS" : (t.status().equals("DONE") ? "IN_PROGRESS" : t.status()));
        tasks.setStatusProgress(taskId, status, progress);
        if (!status.equals(t.status())) {
            tasks.setPosition(taskId, tasks.maxPosition(t.projectId(), status) + 1);
        }
        audit.record(AuthUser.currentId(), "PROGRESS", "TASK", taskId, Map.of("from", t.progress(), "to", progress));
        return get(taskId);
    }

    private void requireCanEdit(TaskRow t) {
        boolean ok = AuthUser.can("TASK_UPDATE_ANY")
                || (AuthUser.can("TASK_UPDATE_OWN") && Objects.equals(t.assigneeId(), AuthUser.currentId()));
        if (!ok) throw ApiException.forbidden("No puedes modificar esta tarea");
    }

    private void publishAssigned(TaskRow t, String projectName, long assigneeId, long by) {
        if (assigneeId == by) return; // no se avisa a quien se asigna a sí mismo
        events.publishEvent(new DomainEvents.TaskAssigned(t.id(), t.projectId(), t.key(), t.title(), projectName, t.dueDate(), assigneeId, by));
    }

    // ------------------------------------------------------------------ comentarios y adjuntos

    public List<CommentDto> comments(long taskId) {
        requireVisibleTask(taskId);
        List<CommentRow> rows = tasks.comments(taskId);
        Map<Long, UserSummary> users = directory.findAll(rows.stream().map(CommentRow::authorId).toList());
        return rows.stream().map(c -> new CommentDto(c.id(), user(users, c.authorId()), c.body(), c.createdAt())).toList();
    }

    @Transactional
    public CommentDto addComment(long taskId, String body) {
        requireVisibleTask(taskId);
        CommentRow c = tasks.addComment(taskId, AuthUser.currentId(), body.trim());
        return new CommentDto(c.id(), user(directory.findAll(List.of(c.authorId())), c.authorId()), c.body(), c.createdAt());
    }

    public List<AttachmentDto> attachments(long taskId) {
        requireVisibleTask(taskId);
        List<AttachmentRow> rows = tasks.attachments(taskId);
        Map<Long, UserSummary> users = directory.findAll(rows.stream().map(AttachmentRow::uploadedBy).toList());
        return rows.stream().map(a -> toDto(a, users)).toList();
    }

    @Transactional
    public AttachmentDto upload(long taskId, MultipartFile file) {
        requireVisibleTask(taskId);
        if (file == null || file.isEmpty()) throw ApiException.badRequest("El archivo está vacío");
        String name = sanitizeName(file.getOriginalFilename());
        String type = file.getContentType() == null ? "application/octet-stream" : file.getContentType();
        String path = storage.save(taskId, file);
        AttachmentRow a = tasks.addAttachment(taskId, AuthUser.currentId(), name, type, file.getSize(), path);
        audit.record(AuthUser.currentId(), "UPLOAD", "TASK", taskId, Map.of("fileName", name, "sizeBytes", file.getSize()));
        return toDto(a, directory.findAll(List.of(a.uploadedBy())));
    }

    public record Download(AttachmentRow meta, Resource resource) {}

    public Download download(long attachmentId) {
        AttachmentRow a = tasks.attachment(attachmentId).orElseThrow(() -> ApiException.notFound("Adjunto"));
        requireVisibleTask(a.taskId());
        Resource r = new FileSystemResource(storage.resolve(a.storagePath()));
        if (!r.exists()) throw ApiException.notFound("Archivo");
        return new Download(a, r);
    }

    private static String sanitizeName(String original) {
        String n = original == null ? "archivo" : original.replace("\\", "/");
        n = n.substring(n.lastIndexOf('/') + 1).replaceAll("[\\p{Cntrl}]", "").trim();
        if (n.isEmpty()) n = "archivo";
        return n.length() > 200 ? n.substring(n.length() - 200) : n;
    }

    // ------------------------------------------------------------------ utilidades

    private AttachmentDto toDto(AttachmentRow a, Map<Long, UserSummary> users) {
        return new AttachmentDto(a.id(), a.fileName(), a.contentType(), a.sizeBytes(), user(users, a.uploadedBy()), a.createdAt());
    }

    private static UserSummary user(Map<Long, UserSummary> map, long id) {
        return map.getOrDefault(id, new UserSummary(id, "Usuario " + id, ""));
    }

    /** Convierte filas en el contrato JSON "Task", resolviendo nombres de personas con una sola consulta. */
    List<TaskView> toViews(List<TaskRow> rows) {
        Set<Long> ids = new HashSet<>();
        for (TaskRow r : rows) {
            ids.add(r.reporterId());
            if (r.assigneeId() != null) ids.add(r.assigneeId());
        }
        Map<Long, UserSummary> users = directory.findAll(ids);
        LocalDate today = LocalDate.now(Dates.ZONE);
        List<TaskView> out = new ArrayList<>(rows.size());
        for (TaskRow r : rows) {
            boolean overdue = r.dueDate() != null && r.dueDate().isBefore(today) && !r.status().equals("DONE");
            out.add(new TaskView(r.id(), r.key(), r.projectId(), r.title(), r.status(), r.priority(),
                    r.assigneeId() == null ? null : user(users, r.assigneeId()), user(users, r.reporterId()),
                    r.dueDate(), overdue, r.progress(), r.position(), r.createdAt(), r.updatedAt()));
        }
        return out;
    }
}
