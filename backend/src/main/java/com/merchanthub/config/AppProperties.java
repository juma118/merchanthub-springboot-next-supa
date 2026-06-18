package com.merchanthub.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Strongly-typed view of the {@code merchanthub.*} configuration block.
 */
@Component
@ConfigurationProperties(prefix = "merchanthub")
public class AppProperties {

    /** Supabase JWT secret (HS256) — validates inbound tokens and signs dev tokens. */
    private String jwtSecret;

    /** Whether the dev-token endpoint is exposed. MUST be false in production. */
    private boolean devAuthEnabled = false;

    /** HMAC secret used to verify inbound shop webhooks. */
    private String webhookSecret;

    /** Base URL of the (mock) shop API used by the pull-sync job. */
    private String shopApiBaseUrl;

    /** Scheduled pull-sync interval in milliseconds. 0 disables the scheduler. */
    private long syncIntervalMs = 300_000;

    /** Comma-separated CORS origins allowed to call the API. */
    private String corsAllowedOrigins = "http://localhost:3000";

    public String getJwtSecret() { return jwtSecret; }
    public void setJwtSecret(String jwtSecret) { this.jwtSecret = jwtSecret; }

    public boolean isDevAuthEnabled() { return devAuthEnabled; }
    public void setDevAuthEnabled(boolean devAuthEnabled) { this.devAuthEnabled = devAuthEnabled; }

    public String getWebhookSecret() { return webhookSecret; }
    public void setWebhookSecret(String webhookSecret) { this.webhookSecret = webhookSecret; }

    public String getShopApiBaseUrl() { return shopApiBaseUrl; }
    public void setShopApiBaseUrl(String shopApiBaseUrl) { this.shopApiBaseUrl = shopApiBaseUrl; }

    public long getSyncIntervalMs() { return syncIntervalMs; }
    public void setSyncIntervalMs(long syncIntervalMs) { this.syncIntervalMs = syncIntervalMs; }

    public String getCorsAllowedOrigins() { return corsAllowedOrigins; }
    public void setCorsAllowedOrigins(String corsAllowedOrigins) { this.corsAllowedOrigins = corsAllowedOrigins; }
}
