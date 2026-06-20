package com.merchanthub.tenant;

import java.util.UUID;

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
