package com.esfx.taskflow.notifications;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

/** Modo desarrollo: no envía nada, solo escribe el correo en el log. Se usa cuando MAIL_HOST está vacío. */
@Component
@ConditionalOnExpression("'${spring.mail.host:}' == ''")
public class LoggingEmailProvider implements EmailProvider {
    private static final Logger log = LoggerFactory.getLogger(LoggingEmailProvider.class);

    @Override
    public void send(String to, String subject, String body) {
        log.info("[CORREO SIMULADO] Para: {} | Asunto: {} | Mensaje: {}", to, subject, body);
    }
}
