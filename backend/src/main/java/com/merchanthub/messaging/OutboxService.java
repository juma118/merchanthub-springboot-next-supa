package com.merchanthub.messaging;

import com.merchanthub.domain.OutboxEvent;
import com.merchanthub.repo.OutboxRepository;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Appends domain events to the outbox. Intentionally NOT annotated
 * {@code @Transactional}: it's always invoked from within a business transaction
 * (order ingestion, inventory update), so the event row commits atomically with
 * the change that produced it.
 */
@Service
public class OutboxService {

    public static final String TOPIC_ORDER_INGESTED = "order.ingested";
    public static final String TOPIC_LOW_STOCK = "inventory.low-stock";

    private final OutboxRepository outbox;

    public OutboxService(OutboxRepository outbox) {
        this.outbox = outbox;
    }

    public void append(String topic, String key, String eventType, Map<String, Object> payload) {
        OutboxEvent e = new OutboxEvent();
        e.setTopic(topic);
        e.setEventKey(key);
        e.setEventType(eventType);
        e.setPayload(payload);
        outbox.save(e);
    }
}
