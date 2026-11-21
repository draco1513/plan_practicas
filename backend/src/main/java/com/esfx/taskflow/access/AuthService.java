package com.esfx.taskflow.access;

import com.esfx.taskflow.access.AccessDtos.CurrentUserDto;
import com.esfx.taskflow.access.AccessDtos.RoleDto;
import com.esfx.taskflow.access.AccessDtos.TokenResponse;
import com.esfx.taskflow.access.AccessDtos.UserDto;
import com.esfx.taskflow.common.ApiException;
import com.esfx.taskflow.common.events.DomainEvents;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

@Service
public class AuthService {
    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final AuditService audit;
    private final ApplicationEventPublisher events;
    private final String frontendUrl;
    private final SecureRandom random = new SecureRandom();

    public AuthService(UserRepository users, PasswordEncoder encoder, JwtService jwt, AuditService audit,
                       ApplicationEventPublisher events, @Value("${app.frontend-url}") String frontendUrl) {
        this.users = users;
        this.encoder = encoder;
        this.jwt = jwt;
        this.audit = audit;
        this.events = events;
        this.frontendUrl = frontendUrl;
    }

    public TokenResponse login(String email, String password) {
        UserRecord u = users.findByEmail(email.trim()).orElse(null);
        // Mismo mensaje para "no existe", "inactivo" o "clave incorrecta": no revela qué falló
        if (u == null || !u.active() || !encoder.matches(password, u.passwordHash())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "BAD_CREDENTIALS", "Correo o contraseña incorrectos");
        }
        users.touchLogin(u.id());
        audit.record(u.id(), "LOGIN", "USER", u.id(), null);
        return new TokenResponse(jwt.create(u.id(), u.email()), "Bearer", jwt.expirationSeconds(), u.mustChangePassword());
    }

    public CurrentUserDto me(long userId) {
        UserRecord u = users.findById(userId).orElseThrow(() -> ApiException.notFound("Usuario"));
        List<String> perms = users.permissionsOfRole(u.roleId()).stream().sorted().toList();
        return new CurrentUserDto(u.id(), u.email(), u.fullName(), new RoleDto(u.roleId(), u.roleCode(), u.roleName()),
                u.active(), u.lastLoginAt(), perms);
    }

    @Transactional
    public void changePassword(long userId, String current, String next) {
        UserRecord u = users.findById(userId).orElseThrow(() -> ApiException.notFound("Usuario"));
        if (!encoder.matches(current, u.passwordHash())) {
            throw ApiException.badRequest("La contraseña actual no es correcta");
        }
        if (encoder.matches(next, u.passwordHash())) {
            throw ApiException.badRequest("La nueva contraseña debe ser distinta de la actual");
        }
        users.setPassword(userId, encoder.encode(next), false);
        audit.record(userId, "PASSWORD_CHANGE", "USER", userId, null);
    }

    /** Siempre responde igual, exista o no el correo, para no revelar qué cuentas existen. */
    @Transactional
    public void forgotPassword(String email) {
        UserRecord u = users.findByEmail(email.trim()).orElse(null);
        if (u == null || !u.active()) return;
        byte[] raw = new byte[32];
        random.nextBytes(raw);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        users.insertResetToken(u.id(), sha256(token), Instant.now().plus(1, ChronoUnit.HOURS));
        String link = frontendUrl.replaceAll("/+$", "") + "/reset-password?token=" + token;
        events.publishEvent(new DomainEvents.PasswordResetRequested(u.id(), u.email(), u.fullName(), link));
        audit.record(u.id(), "PASSWORD_RESET_REQUEST", "USER", u.id(), null);
    }

    @Transactional
    public void resetPassword(String token, String newPassword) {
        String hash = sha256(token);
        Long userId = users.findValidResetToken(hash)
                .orElseThrow(() -> ApiException.badRequest("El enlace no es válido o ya venció"));
        users.setPassword(userId, encoder.encode(newPassword), false);
        users.markResetTokenUsed(hash);
        audit.record(userId, "PASSWORD_RESET", "USER", userId, Map.of());
    }

    static String sha256(String value) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    static UserDto toDto(UserRecord u) {
        return new UserDto(u.id(), u.email(), u.fullName(), new RoleDto(u.roleId(), u.roleCode(), u.roleName()),
                u.active(), u.lastLoginAt());
    }
}
