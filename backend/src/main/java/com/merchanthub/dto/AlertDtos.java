package com.merchanthub.dto;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public final class AlertDtos {
    private AlertDtos() {}

    public record AlertResponse(
            UUID id,
            String type,
            Map<String, Object> payload,
            boolean read,
            Instant createdAt) {}
}
