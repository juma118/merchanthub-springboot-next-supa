package com.merchanthub.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Strongly-typed view of the {@code merchanthub.*} configuration block.
 */
@Component
@ConfigurationProperties(prefix = "merchanthub")
public class AppProperties {

    /** HS256 secret this service signs and validates its own JWTs with. */
    private String jwtSecret;

    /** HMAC secret used to verify inbound shop webhooks. */
    private String webhookSecret;

    /** Base URL of the (mock) shop API used by the pull-sync job. */
    private String shopApiBaseUrl;

    /** Scheduled pull-sync interval in milliseconds. 0 disables the scheduler. */
    private long syncIntervalMs = 300_000;

    /** Comma-separated CORS origins allowed to call the API. */
    private String corsAllowedOrigins = "http://localhost:3000";

    /** Anthropic API key for the AI daily-insights feature. Blank disables it. */
    private String anthropicApiKey;

    /** Anthropic model used to generate the daily insights summary. */
    private String anthropicModel = "claude-haiku-4-5-20251001";

    /** S3 bucket order-report exports are uploaded to. Blank disables the feature. */
    private String reportsS3Bucket;

    /** Optional S3-compatible endpoint override (e.g. LocalStack) for local dev. */
    private String reportsS3EndpointOverride;

    public String getJwtSecret() { return jwtSecret; }
    public void setJwtSecret(String jwtSecret) { this.jwtSecret = jwtSecret; }

    public String getWebhookSecret() { return webhookSecret; }
    public void setWebhookSecret(String webhookSecret) { this.webhookSecret = webhookSecret; }

    public String getShopApiBaseUrl() { return shopApiBaseUrl; }
    public void setShopApiBaseUrl(String shopApiBaseUrl) { this.shopApiBaseUrl = shopApiBaseUrl; }

    public long getSyncIntervalMs() { return syncIntervalMs; }
    public void setSyncIntervalMs(long syncIntervalMs) { this.syncIntervalMs = syncIntervalMs; }

    public String getCorsAllowedOrigins() { return corsAllowedOrigins; }
    public void setCorsAllowedOrigins(String corsAllowedOrigins) { this.corsAllowedOrigins = corsAllowedOrigins; }

    public String getAnthropicApiKey() { return anthropicApiKey; }
    public void setAnthropicApiKey(String anthropicApiKey) { this.anthropicApiKey = anthropicApiKey; }

    public String getAnthropicModel() { return anthropicModel; }
    public void setAnthropicModel(String anthropicModel) { this.anthropicModel = anthropicModel; }

    public String getReportsS3Bucket() { return reportsS3Bucket; }
    public void setReportsS3Bucket(String reportsS3Bucket) { this.reportsS3Bucket = reportsS3Bucket; }

    public String getReportsS3EndpointOverride() { return reportsS3EndpointOverride; }
    public void setReportsS3EndpointOverride(String reportsS3EndpointOverride) { this.reportsS3EndpointOverride = reportsS3EndpointOverride; }
}
