package com.merchanthub.tenant;

import java.util.UUID;

/**
 * Holds the current merchant (tenant) for the executing thread. Populated by the
 * JWT filter for API requests, and explicitly by the webhook receiver and the
 * scheduled sync job for non-request threads. Read by {@link TenantIsolationAspect}
 * to scope the database session.
 */
public final class TenantContext {

    private static final ThreadLocal<UUID> CURRENT = new ThreadLocal<>();

    private TenantContext() {}

    public static void setMerchantId(UUID merchantId) {
        CURRENT.set(merchantId);
    }

    public static UUID getMerchantId() {
        return CURRENT.get();
    }

    public static UUID requireMerchantId() {
        UUID id = CURRENT.get();
        if (id == null) {
            throw new IllegalStateException("No tenant in context — request is not authenticated");
        }
        return id;
    }

    public static void clear() {
        CURRENT.remove();
    }
}
