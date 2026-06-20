package com.merchanthub.notification;

import java.time.Instant;

/** A notification this service "sent" (simulated) in response to a domain event. */
public record Notification(
        String channel,      // e.g. "email", "slack"
        String topic,        // the Kafka topic it came from
        String eventType,    // OrderIngested / LowStockDetected
        String merchantId,
        String message,      // human-readable summary
        Instant receivedAt) {}
