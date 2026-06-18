package com.merchanthub.integration;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** Shapes returned by the (mock) external shop API. */
public final class ShopDtos {
    private ShopDtos() {}

    public record ShopProduct(
            @JsonProperty("external_id") String externalId,
            @JsonProperty("sku") String sku,
            @JsonProperty("name") String name,
            @JsonProperty("description") String description,
            @JsonProperty("price") BigDecimal price,
            @JsonProperty("image_url") String imageUrl,
            @JsonProperty("quantity") Integer quantity) {}

    public record ShopProductsResponse(@JsonProperty("products") List<ShopProduct> products) {}

    public record ShopItem(
            @JsonProperty("sku") String sku,
            @JsonProperty("quantity") int quantity,
            @JsonProperty("unit_price") BigDecimal unitPrice) {}

    public record ShopOrder(
            @JsonProperty("external_id") String externalId,
            @JsonProperty("total") BigDecimal total,
            @JsonProperty("currency") String currency,
            @JsonProperty("status") String status,
            @JsonProperty("customer_email") String customerEmail,
            @JsonProperty("created_at") Instant createdAt,
            @JsonProperty("items") List<ShopItem> items) {}

    public record ShopOrdersResponse(@JsonProperty("orders") List<ShopOrder> orders) {}
}
