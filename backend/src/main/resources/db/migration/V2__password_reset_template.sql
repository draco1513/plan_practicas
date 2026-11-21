-- Plantilla del correo de recuperación de contraseña (se envía directo, no se guarda en la tabla de notificaciones)
INSERT INTO notifications.notification_templates (code, channel, subject_template, body_template) VALUES
    ('PASSWORD_RESET', 'EMAIL', 'Recupera tu contraseña de ESFX TaskFlow',
        'Hola {{userName}}, recibimos una solicitud para restablecer tu contraseña. Entra a este enlace (vale 1 hora): {{link}} . Si no fuiste tú, ignora este mensaje.');
