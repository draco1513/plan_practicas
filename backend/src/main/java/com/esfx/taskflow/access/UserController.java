package com.esfx.taskflow.access;

import com.esfx.taskflow.access.AccessDtos.*;
import com.esfx.taskflow.common.AuthUser;
import com.esfx.taskflow.common.Dates;
import com.esfx.taskflow.common.PageResponse;
import com.esfx.taskflow.common.Paging;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
public class UserController {
    private final UserService users;
    private final AuditService audit;

    public UserController(UserService users, AuditService audit) {
        this.users = users;
        this.audit = audit;
    }

    @GetMapping("/users")
    @PreAuthorize("hasAuthority('USER_MANAGE')")
    public PageResponse<UserDto> list(@RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size,
                                      @RequestParam(required = false) String q, @RequestParam(required = false) Boolean active) {
        return users.list(q, active, Paging.of(page, size));
    }

    @PostMapping("/users")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('USER_MANAGE')")
    public UserDto create(@Valid @RequestBody UserCreate req) { return users.create(AuthUser.currentId(), req); }

    @GetMapping("/users/{id}")
    public UserDto get(@PathVariable long id) { return users.get(id); }

    @PutMapping("/users/{id}")
    @PreAuthorize("hasAuthority('USER_MANAGE')")
    public UserDto update(@PathVariable long id, @Valid @RequestBody UserUpdate req) {
        return users.update(AuthUser.currentId(), id, req);
    }

    @PatchMapping("/users/{id}/status")
    @PreAuthorize("hasAuthority('USER_MANAGE')")
    public UserDto status(@PathVariable long id, @Valid @RequestBody StatusRequest req) {
        return users.setActive(AuthUser.currentId(), id, req.active());
    }

    @GetMapping("/roles")
    public List<RoleDto> roles() { return users.roles(); }

    @GetMapping("/audit-logs")
    @PreAuthorize("hasAuthority('AUDIT_VIEW')")
    public PageResponse<AuditLogDto> audit(@RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size,
                                           @RequestParam(required = false) String entityType,
                                           @RequestParam(required = false) Long actorUserId,
                                           @RequestParam(required = false) String from,
                                           @RequestParam(required = false) String to) {
        return audit.search(entityType, actorUserId, Dates.parse(from, false), Dates.parse(to, true), Paging.of(page, size));
    }
}
