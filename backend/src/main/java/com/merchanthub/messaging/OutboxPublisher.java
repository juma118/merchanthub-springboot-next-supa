package com.merchanthub.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.merchanthub.domain.OutboxEvent;
import com.merchanthub.repo.OutboxRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Relays unpublished outbox rows to Kafka on a fixed schedule and marks them
 * published. Runs on a background thread (no tenant context — the outbox table
 * has no RLS). Sends synchronously and only marks a row published after Kafka
 * acknowledges, so delivery is at-least-once; consumers must be idempotent.
 */
@Component
public class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);

    private final OutboxRepository outbox;
    private final KafkaTemplate<String, String> kafka;
    private final ObjectMapper mapper;

    public OutboxPublisher(OutboxRepository outbox, KafkaTemplate<String, String> kafka, ObjectMapper mapper) {
        this.outbox = outbox;
        this.kafka = kafka;
        this.mapper = mapper;
    }

    @org.springframework.scheduling.annotation.Scheduled(fixedDelayString = "${merchanthub.outbox-poll-ms:2000}")
    @Transactional
    public void publishPending() {
        List<OutboxEvent> pending = outbox.findTop200ByPublishedAtIsNullOrderByCreatedAtAsc();
        if (pending.isEmpty()) return;

        int sent = 0;
        for (OutboxEvent e : pending) {
            try {
                String json = mapper.writeValueAsString(e.getPayload());
                // Block until the broker acknowledges; throws if Kafka is unavailable.
                kafka.send(e.getTopic(), e.getEventKey(), json).get();
                e.setPublishedAt(Instant.now());
                outbox.save(e);
                sent++;
            } catch (Exception ex) {
                // Leave this (and the rest) unpublished; retry on the next tick.
                log.warn("Outbox publish failed for event {} ({}): {} — will retry",
                        e.getId(), e.getTopic(), ex.getMessage());
                break;
            }
        }
        if (sent > 0) log.info("Published {} outbox event(s) to Kafka", sent);
    }
}
