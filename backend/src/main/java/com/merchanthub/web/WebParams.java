package com.merchanthub.web;

import com.merchanthub.web.error.ApiExceptions;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

/** Lenient query-param parsing shared by controllers. */
final class WebParams {
    private WebParams() {}

    /** Accepts a full ISO-8601 instant or a bare {@code YYYY-MM-DD} (treated as UTC midnight). */
    static Instant parseInstant(String value) {
        if (value == null || value.isBlank()) return null;
        String v = value.trim();
        try {
            if (v.length() == 10) {
                return LocalDate.parse(v).atStartOfDay(ZoneOffset.UTC).toInstant();
            }
            return Instant.parse(v);
        } catch (Exception e) {
            throw new ApiExceptions.BadRequest("Invalid date '" + value + "' (expected ISO-8601 or YYYY-MM-DD)");
        }
    }
}
