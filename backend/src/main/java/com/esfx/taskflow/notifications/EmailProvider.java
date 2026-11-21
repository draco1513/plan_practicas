package com.esfx.taskflow.notifications;

/**
 * Proveedor de correo. Hoy hay dos implementaciones: SMTP (Gmail) y "solo log" para desarrollo.
 * Más adelante se puede agregar Outlook / Microsoft Graph sin tocar el resto del módulo.
 */
public interface EmailProvider {
    void send(String to, String subject, String body) throws Exception;
}
