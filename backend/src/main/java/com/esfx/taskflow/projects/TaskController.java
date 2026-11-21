package com.esfx.taskflow.projects;

import com.esfx.taskflow.common.PageResponse;
import com.esfx.taskflow.common.Paging;
import com.esfx.taskflow.common.ProjectsApi.TaskView;
import com.esfx.taskflow.projects.ProjectsDtos.*;
import jakarta.validation.Valid;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api")
public class TaskController {
    private final TaskService service;

    public TaskController(TaskService service) { this.service = service; }

    @GetMapping("/projects/{projectId}/tasks")
    public PageResponse<TaskView> list(@PathVariable long projectId, @RequestParam(required = false) Integer page,
                                       @RequestParam(required = false) Integer size, @RequestParam(required = false) String status,
                                       @RequestParam(required = false) Long assigneeId, @RequestParam(required = false) Boolean overdue) {
        return service.list(projectId, status, assigneeId, Boolean.TRUE.equals(overdue), Paging.of(page, size));
    }

    @GetMapping("/projects/{projectId}/board")
    public BoardDto board(@PathVariable long projectId) { return service.board(projectId); }

    @PostMapping("/projects/{projectId}/tasks")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('TASK_CREATE')")
    public TaskView create(@PathVariable long projectId, @Valid @RequestBody TaskCreate req) { return service.create(projectId, req); }

    @GetMapping("/tasks/{taskId}")
    public TaskView get(@PathVariable long taskId) { return service.get(taskId); }

    // La regla "TASK_UPDATE_ANY o TASK_UPDATE_OWN sobre tareas propias" se valida dentro del servicio
    @PutMapping("/tasks/{taskId}")
    @PreAuthorize("hasAnyAuthority('TASK_UPDATE_ANY','TASK_UPDATE_OWN')")
    public TaskView update(@PathVariable long taskId, @Valid @RequestBody TaskUpdate req) { return service.update(taskId, req); }

    @PatchMapping("/tasks/{taskId}/assignee")
    @PreAuthorize("hasAuthority('TASK_ASSIGN')")
    public TaskView assign(@PathVariable long taskId, @RequestBody AssigneeRequest req) { return service.assign(taskId, req.assigneeId()); }

    @PatchMapping("/tasks/{taskId}/move")
    @PreAuthorize("hasAnyAuthority('TASK_UPDATE_ANY','TASK_UPDATE_OWN')")
    public TaskView move(@PathVariable long taskId, @Valid @RequestBody MoveRequest req) {
        return service.move(taskId, req.status(), req.position());
    }

    @PatchMapping("/tasks/{taskId}/progress")
    @PreAuthorize("hasAnyAuthority('TASK_UPDATE_ANY','TASK_UPDATE_OWN')")
    public TaskView progress(@PathVariable long taskId, @Valid @RequestBody ProgressRequest req) {
        return service.setProgress(taskId, req.progress());
    }

    @GetMapping("/tasks/{taskId}/comments")
    public List<CommentDto> comments(@PathVariable long taskId) { return service.comments(taskId); }

    @PostMapping("/tasks/{taskId}/comments")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('COMMENT_CREATE')")
    public CommentDto addComment(@PathVariable long taskId, @Valid @RequestBody CommentRequest req) {
        return service.addComment(taskId, req.body());
    }

    @GetMapping("/tasks/{taskId}/attachments")
    public List<AttachmentDto> attachments(@PathVariable long taskId) { return service.attachments(taskId); }

    @PostMapping(value = "/tasks/{taskId}/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('COMMENT_CREATE')")
    public AttachmentDto upload(@PathVariable long taskId, @RequestParam("file") MultipartFile file) {
        return service.upload(taskId, file);
    }

    @GetMapping("/attachments/{attachmentId}/download")
    public ResponseEntity<Resource> download(@PathVariable long attachmentId) {
        TaskService.Download d = service.download(attachmentId);
        MediaType type;
        try {
            type = MediaType.parseMediaType(d.meta().contentType());
        } catch (Exception e) {
            type = MediaType.APPLICATION_OCTET_STREAM;
        }
        return ResponseEntity.ok()
                .contentType(type)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(d.meta().fileName(), StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(d.resource());
    }
}
