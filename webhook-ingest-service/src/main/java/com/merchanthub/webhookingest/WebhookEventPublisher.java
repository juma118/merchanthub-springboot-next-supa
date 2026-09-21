package com.merchanthub.webhookingest;

import io.smallrye.reactive.messaging.kafka.Record;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;

import java.util.UUID;

/**
 * Republishes a validated inbound order onto Kafka for the backend's
 * {@code WebhookEventListener} to persist — this service never talks to the
 * backend directly, only to Postgres (read-only lookup) and Kafka.
 */
@ApplicationScoped
public class WebhookEventPublisher {

    @Channel("order-webhook-received")
    Emitter<Record<String, String>> emitter;

    public void publish(UUID merchantId, String rawOrderJson) {
        emitter.send(Record.of(merchantId.toString(), rawOrderJson));
    }
}
