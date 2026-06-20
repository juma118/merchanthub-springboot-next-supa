package com.merchanthub.notification;

import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * In-memory ring buffer of recent notifications. A real service would persist
 * these or actually call an email/Slack provider; for the demo we keep the last N
 * and expose them over REST. Thread-safe because Kafka listener threads write
 * while HTTP threads read.
 */
@Component
public class NotificationStore {

    private static final int MAX = 500;
    private final Deque<Notification> recent = new ArrayDeque<>();

    public synchronized void add(Notification n) {
        recent.addFirst(n);
        while (recent.size() > MAX) recent.removeLast();
    }

    /** Newest first. */
    public synchronized List<Notification> list() {
        return new ArrayList<>(recent);
    }

    public synchronized int count() {
        return recent.size();
    }
}
