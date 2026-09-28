package com.tutorcraft.core.communication.notifications.web;

import com.tutorcraft.core.communication.notifications.application.NotificationCenterService;
import com.tutorcraft.core.communication.notifications.application.NotificationCenterService.Inbox;
import com.tutorcraft.core.communication.notifications.application.NotificationCenterService.NotificationView;
import com.tutorcraft.core.shared.api.PageQuery;
import com.tutorcraft.core.shared.api.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Центр уведомлений и настройки (контракт §2). */
@RestController
@RequestMapping("/api/v1/me")
class NotificationsController {

    static final String UNREAD_COUNT_HEADER = "X-Unread-Count";

    private final NotificationCenterService center;

    NotificationsController(NotificationCenterService center) {
        this.center = center;
    }

    @GetMapping("/notifications")
    ResponseEntity<PageResponse<NotificationView>> list(@RequestParam(required = false) String cursor,
                                                        @RequestParam(required = false) Integer limit) {
        Inbox inbox = center.inbox(PageQuery.of(cursor, limit));
        return ResponseEntity.ok().header(UNREAD_COUNT_HEADER, Long.toString(inbox.unreadCount())).body(inbox.page());
    }

    @PostMapping("/notifications/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void markRead(@RequestBody ReadRequest request) {
        center.markRead(request.ids(), Boolean.TRUE.equals(request.all()));
    }

    @GetMapping("/notification-preferences")
    Preferences preferences() {
        return new Preferences(center.preferences());
    }

    @PutMapping("/notification-preferences")
    Preferences updatePreferences(@Valid @RequestBody Preferences request) {
        return new Preferences(center.updatePreferences(request.matrix()));
    }

    record ReadRequest(List<UUID> ids, Boolean all) {
    }

    /** Контракт NotificationPreferences: matrix[категория][канал] = включено. */
    record Preferences(@NotNull Map<String, Map<String, Boolean>> matrix) {
    }
}
