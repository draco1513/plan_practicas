package com.esfx.taskflow.access;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Crea el primer administrador con APP_ADMIN_EMAIL / APP_ADMIN_PASSWORD si todavía no hay usuarios.
 * La contraseña nunca está escrita en el repositorio ni en la base de datos en texto plano.
 */
@Component
public class AdminSeeder implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(AdminSeeder.class);

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final String email;
    private final String password;

    public AdminSeeder(UserRepository users, PasswordEncoder encoder,
                       @Value("${app.admin.email}") String email, @Value("${app.admin.password}") String password) {
        this.users = users;
        this.encoder = encoder;
        this.email = email;
        this.password = password;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (users.countAll() > 0) return;
        if (password == null || password.length() < 8) {
            log.warn("No hay usuarios y APP_ADMIN_PASSWORD no está definida (mínimo 8 caracteres): no se creó el administrador.");
            return;
        }
        int roleId = users.roleIdByCode("ADMIN").orElseThrow(() -> new IllegalStateException("Falta el rol ADMIN"));
        users.insert(email.trim(), encoder.encode(password), "Administrador", roleId, true);
        log.info("Administrador inicial creado: {} (deberá cambiar la contraseña al entrar).", email);
    }
}
