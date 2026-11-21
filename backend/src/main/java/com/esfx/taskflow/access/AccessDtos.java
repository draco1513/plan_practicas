package com.esfx.taskflow.access;

import com.esfx.taskflow.common.UserSummary;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Objetos de entrada y salida de la API del módulo access. */
public final class AccessDtos {
    private AccessDtos() {}

    public record LoginRequest(@NotBlank @Email String email, @NotBlank String password) {}
    public record TokenResponse(String accessToken, String tokenType, long expiresIn, boolean mustChangePassword) {}
    public record ChangePasswordRequest(@NotBlank String currentPassword,
                                        @NotBlank @Size(min = 8, max = 72, message = "Debe tener entre 8 y 72 caracteres") String newPassword) {}
    public record ForgotPasswordRequest(@NotBlank @Email String email) {}
    public record ResetPasswordRequest(@NotBlank String token,
                                       @NotBlank @Size(min = 8, max = 72, message = "Debe tener entre 8 y 72 caracteres") String newPassword) {}

    public record RoleDto(int id, String code, String name) {}
    public record UserDto(long id, String email, String fullName, RoleDto role, boolean active, Instant lastLoginAt) {}
    public record CurrentUserDto(long id, String email, String fullName, RoleDto role, boolean active, Instant lastLoginAt,
                                 List<String> permissions) {}

    public record UserCreate(@NotBlank @Email @Size(max = 255) String email,
                             @NotBlank @Size(max = 120) String fullName,
                             @NotNull Integer roleId,
                             @NotBlank @Size(min = 8, max = 72, message = "Debe tener entre 8 y 72 caracteres") String temporaryPassword) {}
    public record UserUpdate(@NotBlank @Size(max = 120) String fullName, @NotNull Integer roleId) {}
    public record StatusRequest(@NotNull Boolean active) {}

    public record AuditLogDto(long id, UserSummary actor, String action, String entityType, Long entityId,
                              Map<String, Object> detail, Instant createdAt) {}
}
