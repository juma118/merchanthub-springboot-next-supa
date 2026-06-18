package com.merchanthub.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class OrderDtos {
    private OrderDtos() {}

    public record OrderSummary(
            UUID id,
            String externalId,
            BigDecimal total,
            String currency,
            String status,
            String customerEmail,
            Instant createdAt,
            long itemCount) {}

    public record OrderItemView(
            UUID id,
            UUID productId,
            String sku,
            int quantity,
            BigDecimal unitPrice) {}

    public record OrderDetail(
            UUID id,
            String externalId,
            BigDecimal total,
            String currency,
            String status,
            String customerEmail,
            Instant createdAt,
            List<OrderItemView> items) {}
}
