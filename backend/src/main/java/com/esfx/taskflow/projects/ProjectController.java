package com.esfx.taskflow.projects;

import com.esfx.taskflow.common.PageResponse;
import com.esfx.taskflow.common.Paging;
import com.esfx.taskflow.common.UserSummary;
import com.esfx.taskflow.projects.ProjectsDtos.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/projects")
public class ProjectController {
    private final ProjectService service;

    public ProjectController(ProjectService service) { this.service = service; }

    @GetMapping
    public PageResponse<ProjectDto> list(@RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size,
                                         @RequestParam(required = false) String status) {
        return service.list(status, Paging.of(page, size));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('PROJECT_CREATE')")
    public ProjectDto create(@Valid @RequestBody ProjectCreate req) { return service.create(req); }

    @GetMapping("/{id}")
    public ProjectDto get(@PathVariable long id) { return service.get(id); }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('PROJECT_EDIT')")
    public ProjectDto update(@PathVariable long id, @Valid @RequestBody ProjectUpdate req) { return service.update(id, req); }

    @GetMapping("/{id}/members")
    public List<UserSummary> members(@PathVariable long id) { return service.members(id); }

    @PutMapping("/{id}/members")
    @PreAuthorize("hasAuthority('PROJECT_EDIT')")
    public List<UserSummary> setMembers(@PathVariable long id, @Valid @RequestBody MembersRequest req) {
        return service.setMembers(id, req.userIds());
    }
}
