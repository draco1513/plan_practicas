package com.esfx.taskflow.access;

import com.esfx.taskflow.access.AccessDtos.*;
import com.esfx.taskflow.common.ApiException;
import com.esfx.taskflow.common.PageResponse;
import com.esfx.taskflow.common.Paging;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
public class UserService {
    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final AuditService audit;

    public UserService(UserRepository users, PasswordEncoder encoder, AuditService audit) {
        this.users = users;
        this.encoder = encoder;
        this.audit = audit;
    }

    public PageResponse<UserDto> list(String q, Boolean active, Paging paging) {
        List<UserDto> rows = users.search(q, active, paging.size(), paging.offset()).stream().map(AuthService::toDto).toList();
        return PageResponse.of(rows, paging.page(), paging.size(), users.count(q, active));
    }

    public UserDto get(long id) {
        return AuthService.toDto(users.findById(id).orElseThrow(() -> ApiException.notFound("Usuario")));
    }

    public List<RoleDto> roles() {
        return users.roles().stream().map(r -> new RoleDto(r.id(), r.code(), r.name())).toList();
    }

    @Transactional
    public UserDto create(long actorId, UserCreate req) {
        String email = req.email().trim();
        if (users.emailExists(email)) throw ApiException.conflict("Ya existe un usuario con ese correo");
        if (!users.roleExists(req.roleId())) throw ApiException.badRequest("El rol indicado no existe");
        long id = users.insert(email, encoder.encode(req.temporaryPassword()), req.fullName().trim(), req.roleId(), true);
        audit.record(actorId, "CREATE", "USER", id, Map.of("email", email, "roleId", req.roleId()));
        return get(id);
    }

    @Transactional
    public UserDto update(long actorId, long id, UserUpdate req) {
        UserRecord before = users.findById(id).orElseThrow(() -> ApiException.notFound("Usuario"));
        if (!users.roleExists(req.roleId())) throw ApiException.badRequest("El rol indicado no existe");
        users.updateProfile(id, req.fullName().trim(), req.roleId());
        audit.record(actorId, "UPDATE", "USER", id, Map.of(
                "fullName", Map.of("from", before.fullName(), "to", req.fullName().trim()),
                "roleId", Map.of("from", before.roleId(), "to", req.roleId())));
        return get(id);
    }

    @Transactional
    public UserDto setActive(long actorId, long id, boolean active) {
        users.findById(id).orElseThrow(() -> ApiException.notFound("Usuario"));
        if (!active && actorId == id) throw ApiException.badRequest("No puedes desactivar tu propia cuenta");
        users.setActive(id, active);
        audit.record(actorId, active ? "ACTIVATE" : "DEACTIVATE", "USER", id, null);
        return get(id);
    }
}
