package com.esfx.taskflow.notifications;

import com.esfx.taskflow.common.ApiException;
import com.esfx.taskflow.common.AuthUser;
import com.esfx.taskflow.common.PageResponse;
import com.esfx.taskflow.common.Paging;
import com.esfx.taskflow.notifications.NotificationDtos.*;
import com.esfx.taskflow.notifications.NotificationRepository.Row;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {
    private final NotificationRepository repo;
    private final ObjectMapper mapper;

    public NotificationController(NotificationRepository repo, ObjectMapper mapper) {
        this.repo = repo;
        this.mapper = mapper;
    }

    @GetMapping
    public PageResponse<NotificationDto> list(@RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size,
                                              @RequestParam(required = false) Boolean unreadOnly) {
        Paging p = Paging.of(page, size);
        long me = AuthUser.currentId();
        boolean unread = Boolean.TRUE.equals(unreadOnly);
        List<NotificationDto> rows = repo.inbox(me, unread, p.size(), p.offset()).stream().map(this::toDto).toList();
        return PageResponse.of(rows, p.page(), p.size(), repo.inboxCount(me, unread));
    }

    @GetMapping("/unread-count")
    public CountDto unread() { return new CountDto(repo.inboxCount(AuthUser.currentId(), true)); }

    @PostMapping("/{id}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void read(@PathVariable long id) {
        if (!repo.markRead(id, AuthUser.currentId())) throw ApiException.notFound("Notificación");
    }

    @PostMapping("/read-all")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void readAll() { repo.markAllRead(AuthUser.currentId()); }

    @GetMapping("/preferences")
    public PreferencesDto preferences() { return repo.preferences(AuthUser.currentId()); }

    @PutMapping("/preferences")
    public PreferencesDto save(@Valid @RequestBody PreferencesDto req) {
        repo.savePreferences(AuthUser.currentId(), req);
        return repo.preferences(AuthUser.currentId());
    }

    private NotificationDto toDto(Row r) {
        Map<String, Object> payload = null;
        if (r.payloadJson() != null) {
            try {
                payload = mapper.readValue(r.payloadJson(), new TypeReference<Map<String, Object>>() {});
            } catch (JsonProcessingException e) {
                payload = null;
            }
        }
        return new NotificationDto(r.id(), r.type(), r.title(), r.body(), payload, r.readAt() != null, r.createdAt());
    }
}
