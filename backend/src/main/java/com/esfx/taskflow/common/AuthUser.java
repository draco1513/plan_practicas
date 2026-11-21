package com.esfx.taskflow.common;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

/** Usuario autenticado de la petición actual (lo coloca el filtro JWT). */
public record AuthUser(long id, String email) {

    public static AuthUser current() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        if (a == null || !(a.getPrincipal() instanceof AuthUser u)) {
            throw new ApiException(org.springframework.http.HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Sesión no válida");
        }
        return u;
    }

    public static long currentId() { return current().id(); }

    public static boolean can(String permission) {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        if (a == null) return false;
        for (GrantedAuthority g : a.getAuthorities()) {
            if (g.getAuthority().equals(permission)) return true;
        }
        return false;
    }
}
