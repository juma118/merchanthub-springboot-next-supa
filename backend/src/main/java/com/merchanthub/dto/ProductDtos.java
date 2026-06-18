package com.merchanthub.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class ProductDtos {
    private ProductDtos() {}

    public record ProductRequest(
            @NotBlank String sku,
            @NotBlank String name,
            String description,
            @NotNull @PositiveOrZero BigDecimal price,
            String imageUrl,
            @Min(0) Integer quantity,
            @Min(0) Integer lowStockThreshold) {}

    public record InventoryView(int quantity, int lowStockThreshold) {}

    public record ProductResponse(
            UUID id,
            String sku,
            String name,
            String description,
            BigDecimal price,
            String imageUrl,
            String externalId,
            Instant createdAt,
            Instant updatedAt,
            InventoryView inventory) {}

    public record InventoryRow(
            UUID productId,
            String sku,
            String name,
            int quantity,
            int lowStockThreshold,
            boolean lowStock) {}

    public record InventoryUpdateRequest(
            @NotNull @Min(0) Integer quantity,
            @NotNull @Min(0) Integer lowStockThreshold) {}

    public record ImportResult(int imported, int updated, List<String> errors) {}
}
