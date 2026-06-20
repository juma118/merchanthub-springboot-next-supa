package com.merchanthub.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;

/**
 * Subscribes to the backend's domain-event topics and turns each event into a
 * (simulated) outbound notification. This is the consumer side of the
 * event-driven boundary between the two services.
 */
@Component
public class EventListener {

    private static final Logger log = LoggerFactory.getLogger(EventListener.class);

    private final NotificationStore store;
    private final ObjectMapper mapper;

    public EventListener(NotificationStore store, ObjectMapper mapper) {
        this.store = store;
        this.mapper = mapper;
    }

    @KafkaListener(
            topics = {"order.ingested", "inventory.low-stock"},
            groupId = "${spring.kafka.consumer.group-id:notification-service}")
    public void onEvent(@Payload String payload, @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        try {
            Map<?, ?> e = mapper.readValue(payload, Map.class);
            String eventType = str(e.get("event"));
            String merchantId = str(e.get("merchantId"));

            String channel;
            String message;
            if ("inventory.low-stock".equals(topic)) {
                channel = "slack";
                message = "⚠️ Low stock: SKU %s is down to %s (threshold %s)"
                        .formatted(str(e.get("sku")), str(e.get("quantity")), str(e.get("threshold")));
            } else {
                channel = "email";
                message = "🧾 New order %s — %s %s (%s)".formatted(
                        str(e.get("externalId")), str(e.get("total")), "USD", str(e.get("status")));
            }

            store.add(new Notification(channel, topic, eventType, merchantId, message, Instant.now()));
            log.info("[{}] → {} notification for merchant {}: {}", topic, channel, merchantId, message);
        } catch (Exception ex) {
            log.error("Failed to handle event from {}: {}", topic, ex.getMessage());
        }
    }

    private static String str(Object o) {
        return o == null ? "" : o.toString();
    }
}
