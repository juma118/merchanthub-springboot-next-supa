package com.merchanthub.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public final class AnalyticsDtos {
    private AnalyticsDtos() {}

    public record RevenuePoint(String bucket, BigDecimal revenue, long orders) {}

    public record PeriodTotals(BigDecimal totalRevenue, long totalOrders) {}

    public record RevenueResponse(
            String granularity,
            List<RevenuePoint> series,
            BigDecimal totalRevenue,
            long totalOrders,
            PeriodTotals previous,
            Double changePct) {}

    public record TopProductItem(UUID productId, String sku, String name, long units, BigDecimal revenue) {}

    public record TopProductsResponse(String metric, List<TopProductItem> items) {}

    public record FunnelResponse(
            long created,
            long paid,
            long fulfilled,
            long cancelled,
            long abandoned,
            double abandonedRate) {}

    public record ForecastItem(
            UUID productId,
            String sku,
            String name,
            int quantity,
            double avgDailyUnits,
            Double daysToStockout,
            String status) {}

    public record ForecastResponse(List<ForecastItem> items) {}
}
