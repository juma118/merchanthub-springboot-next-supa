package com.merchanthub.notification;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
public class NotificationController {

    private final NotificationStore store;

    public NotificationController(NotificationStore store) {
        this.store = store;
    }

    @GetMapping("/")
    public Map<String, Object> info() {
        return Map.of(
                "service", "notification-service",
                "consumes", List.of("order.ingested", "inventory.low-stock"),
                "notificationsHeld", store.count());
    }

    /** Recent notifications this service produced from consumed events (newest first). */
    @GetMapping("/notifications")
    public List<Notification> notifications() {
        return store.list();
    }
}
