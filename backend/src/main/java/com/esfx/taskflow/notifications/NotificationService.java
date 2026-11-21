package com.esfx.taskflow.notifications;

import com.esfx.taskflow.common.UserDirectory;
import com.esfx.taskflow.common.UserSummary;
import com.esfx.taskflow.notifications.NotificationDtos.PreferencesDto;
import com.esfx.taskflow.notifications.NotificationRepository.Template;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Núcleo del módulo: una alerta (evento) dispara una plantilla y deja la notificación guardada.
 * El correo queda PENDING y se encola; el worker lo entrega al proveedor y lo marca SENT o FAILED.
 */
@Service
public class NotificationService {
    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationRepository repo;
    private final NotificationQueue queue;
    private final UserDirectory directory;
    private final ObjectMapper mapper;

    public NotificationService(NotificationRepository repo, NotificationQueue queue, UserDirectory directory, ObjectMapper mapper) {
        this.repo = repo;
        this.queue = queue;
        this.directory = directory;
        this.mapper = mapper;
    }

    /**
     * @param type            código de la plantilla (TASK_ASSIGNED, MEETING_INVITATION...)
     * @param vars            valores para los marcadores {{...}}
     * @param payload         ids para que la pantalla enlace a la tarea o reunión
     * @param idempotencyKey  evita duplicados si el mismo aviso se dispara dos veces
     */
    public void notifyUser(long userId, String type, Map<String, String> vars, Map<String, Object> payload, String idempotencyKey) {
        Optional<UserSummary> user = directory.find(userId);
        if (user.isEmpty() || !directory.existsActive(userId)) return;
        Map<String, String> all = new HashMap<>(vars);
        all.put("userName", user.get().fullName());
        String payloadJson = toJson(payload);
        Instant now = Instant.now();

        // 1) Aviso dentro de la aplicación: queda entregado de inmediato
        render(type, "IN_APP", all).ifPresent(r ->
                repo.insert(userId, null, type, "IN_APP", r.subject(), r.body(), payloadJson, "SENT", idempotencyKey + ":app", now));

        // 2) Correo: solo si el usuario lo permite; queda PENDING y se encola
        PreferencesDto prefs = repo.preferences(userId);
        if (Boolean.TRUE.equals(prefs.emailEnabled())) {
            render(type, "EMAIL", all).ifPresent(r ->
                    repo.insert(userId, user.get().email(), type, "EMAIL", r.subject(), r.body(), payloadJson, "PENDING",
                            idempotencyKey + ":mail", now).ifPresent(queue::push));
        }
    }

    private record Rendered(String subject, String body) {}

    private Optional<Rendered> render(String type, String channel, Map<String, String> vars) {
        Optional<Template> t = repo.template(type, channel);
        if (t.isEmpty()) {
            log.warn("No existe la plantilla {} para el canal {}", type, channel);
            return Optional.empty();
        }
        return Optional.of(new Rendered(TemplateRenderer.render(t.get().subject(), vars), TemplateRenderer.render(t.get().body(), vars)));
    }

    /** Renderiza una plantilla de correo (sin guardar nada); se usa para el correo de recuperación de contraseña. */
    public Optional<String[]> renderEmail(String type, Map<String, String> vars) {
        return render(type, "EMAIL", vars).map(r -> new String[]{r.subject(), r.body()});
    }

    private String toJson(Map<String, Object> payload) {
        if (payload == null) return null;
        try {
            return mapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            return null;
        }
    }
}
