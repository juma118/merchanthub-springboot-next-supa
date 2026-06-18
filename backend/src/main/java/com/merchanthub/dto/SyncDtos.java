package com.merchanthub.dto;

import java.time.Instant;
import java.util.UUID;

public final class SyncDtos {
    private SyncDtos() {}

    public record SyncRunResponse(UUID syncLogId, String status, int recordsProcessed) {}

    public record SyncLogResponse(
            UUID id,
            String type,
            String status,
            String detail,
            int recordsProcessed,
            Instant startedAt,
            Instant finishedAt) {}
}
