package com.merchanthub.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class WebhookDtos {
    private WebhookDtos() {}

    /** Inbound webhook body delivered by the shop API. See mock-shop-api. */
    public record WebhookPayload(
            @JsonProperty("apiKey") String apiKey,
            @JsonProperty("order") WebhookOrder order) {}

    public record WebhookOrder(
            @JsonProperty("external_id") String externalId,
            @JsonProperty("total") BigDecimal total,
            @JsonProperty("currency") String currency,
            @JsonProperty("status") String status,
            @JsonProperty("customer_email") String customerEmail,
            @JsonProperty("created_at") Instant createdAt,
            @JsonProperty("items") List<WebhookItem> items) {}

    public record WebhookItem(
            @JsonProperty("sku") String sku,
            @JsonProperty("quantity") int quantity,
            @JsonProperty("unit_price") BigDecimal unitPrice) {}
}
