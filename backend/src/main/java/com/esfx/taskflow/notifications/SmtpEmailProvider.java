package com.esfx.taskflow.notifications;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/** Envío real por SMTP (por ejemplo smtp.gmail.com con una contraseña de aplicación). Se activa si MAIL_HOST tiene valor. */
@Component
@ConditionalOnExpression("'${spring.mail.host:}' != ''")
public class SmtpEmailProvider implements EmailProvider {
    private final JavaMailSender sender;
    private final String from;

    public SmtpEmailProvider(JavaMailSender sender, @Value("${app.mail.from:}") String from,
                             @Value("${spring.mail.username:}") String username) {
        this.sender = sender;
        this.from = from != null && !from.isBlank() ? from : username;
    }

    @Override
    public void send(String to, String subject, String body) {
        SimpleMailMessage msg = new SimpleMailMessage();
        if (from != null && !from.isBlank()) msg.setFrom(from);
        msg.setTo(to);
        msg.setSubject(subject);
        msg.setText(body);
        sender.send(msg);
    }
}
