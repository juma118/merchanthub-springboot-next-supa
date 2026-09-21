package com.merchanthub.dto;

import java.time.Instant;

public final class ReportDtos {
    private ReportDtos() {}

    public record ExportResponse(String s3Key, String downloadUrl, Instant downloadUrlExpiresAt) {}
}
