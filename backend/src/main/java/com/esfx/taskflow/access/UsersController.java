package com.esfx.taskflow.access;

import com.esfx.taskflow.access.AccessDtos.CurrentUserDto;
import com.esfx.taskflow.common.AuthUser;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
public class UsersController {
    private final AuthService auth;

    public UsersController(AuthService auth) { this.auth = auth; }

    @GetMapping("/me")
    public CurrentUserDto me() { return auth.me(AuthUser.currentId()); }
}
