package com.merchanthub.service;

import com.merchanthub.dto.AnalyticsDtos.*;
import com.merchanthub.tenant.TenantContext;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
public class AnalyticsService {

    private final NamedParameterJdbcTemplate jdbc;

    public AnalyticsService(DataSource dataSource) {
        this.jdbc = new NamedParameterJdbcTemplate(dataSource);
    }

    private static final Set<String> REVENUE_STATUSES = Set.of("paid", "fulfilled");

    @Transactional(readOnly = true)
    public RevenueResponse revenue(String granularity, Instant from, Instant to) {
        String gran = switch (granularity == null ? "day" : granularity.toLowerCase()) {
            case "week" -> "week";
            case "month" -> "month";
            default -> "day";
        };
        String fmt = gran.equals("month") ? "YYYY-MM" : "YYYY-MM-DD";

        Instant end = to != null ? to : Instant.now();
        Instant start = from != null ? from : defaultStart(gran, end);
        Duration window = Duration.between(start, end);
        Instant prevStart = start.minus(window);

        var params = new MapSqlParameterSource()
                .addValue("mid", TenantContext.requireMerchantId())
                .addValue("gran", gran)
                .addValue("fmt", fmt)
                .addValue("from", Timestamp.from(start))
                .addValue("to", Timestamp.from(end));

        List<RevenuePoint> series = jdbc.query("""
                select to_char(date_trunc(:gran, created_at), :fmt) as bucket,
                       coalesce(sum(total), 0) as revenue,
                       count(*) as orders
                from orders
                where merchant_id = :mid
                  and status in ('paid', 'fulfilled')
                  and created_at >= :from and created_at < :to
                group by 1
                order by 1
                """, params, (rs, n) -> new RevenuePoint(
                        rs.getString("bucket"),
                        rs.getBigDecimal("revenue"),
                        rs.getLong("orders")));

        PeriodTotals current = totals(start, end);
        PeriodTotals previous = totals(prevStart, start);
        Double changePct = (previous.totalRevenue() != null
                && previous.totalRevenue().compareTo(BigDecimal.ZERO) > 0)
                ? current.totalRevenue().subtract(previous.totalRevenue())
                        .divide(previous.totalRevenue(), 4, java.math.RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100)).doubleValue()
                : null;

        return new RevenueResponse(gran, series, current.totalRevenue(), current.totalOrders(), previous, changePct);
    }

    private PeriodTotals totals(Instant from, Instant to) {
        var params = new MapSqlParameterSource()
                .addValue("mid", TenantContext.requireMerchantId())
                .addValue("from", Timestamp.from(from))
                .addValue("to", Timestamp.from(to));
        return jdbc.queryForObject("""
                select coalesce(sum(total), 0) as revenue, count(*) as orders
                from orders
                where merchant_id = :mid
                  and status in ('paid', 'fulfilled')
                  and created_at >= :from and created_at < :to
                """, params, (rs, n) -> new PeriodTotals(rs.getBigDecimal("revenue"), rs.getLong("orders")));
    }

    @Transactional(readOnly = true)
    public TopProductsResponse topProducts(String metric, int limit) {
        // metric is whitelisted to a column name, so it is safe to interpolate
        // (and must be — a bind parameter can't name an ORDER BY column).
        String orderBy = "units".equalsIgnoreCase(metric) ? "units" : "revenue";
        var params = new MapSqlParameterSource()
                .addValue("mid", TenantContext.requireMerchantId())
                .addValue("lim", Math.min(Math.max(limit, 1), 50));

        String sql = """
                select p.id as product_id, p.sku, p.name,
                       coalesce(sum(oi.quantity), 0) as units,
                       coalesce(sum(oi.quantity * oi.unit_price), 0) as revenue
                from order_items oi
                join orders o on o.id = oi.order_id and o.status in ('paid', 'fulfilled')
                join products p on p.id = oi.product_id
                where oi.merchant_id = :mid
                group by p.id, p.sku, p.name
                order by %s desc
                limit :lim
                """.formatted(orderBy);
        List<TopProductItem> items = jdbc.query(sql, params, (rs, n) -> new TopProductItem(
                        rs.getObject("product_id", java.util.UUID.class),
                        rs.getString("sku"), rs.getString("name"),
                        rs.getLong("units"), rs.getBigDecimal("revenue")));
        return new TopProductsResponse(orderBy, items);
    }

    @Transactional(readOnly = true)
    public FunnelResponse funnel(Instant from, Instant to) {
        Instant start = from != null ? from : Instant.EPOCH;
        Instant end = to != null ? to : Instant.parse("2999-12-31T23:59:59Z");
        var params = new MapSqlParameterSource()
                .addValue("mid", TenantContext.requireMerchantId())
                .addValue("from", Timestamp.from(start))
                .addValue("to", Timestamp.from(end));

        Map<String, Long> counts = new HashMap<>();
        jdbc.query("""
                select status, count(*) as c
                from orders
                where merchant_id = :mid
                  and created_at >= :from
                  and created_at < :to
                group by status
                """, params, rs -> { counts.put(rs.getString("status"), rs.getLong("c")); });

        long created = counts.getOrDefault("created", 0L);
        long paid = counts.getOrDefault("paid", 0L);
        long fulfilled = counts.getOrDefault("fulfilled", 0L);
        long cancelled = counts.getOrDefault("cancelled", 0L);
        long abandoned = counts.getOrDefault("abandoned", 0L);
        long all = created + paid + fulfilled + cancelled + abandoned;
        double abandonedRate = all == 0 ? 0.0 : (double) abandoned / all;
        return new FunnelResponse(created, paid, fulfilled, cancelled, abandoned,
                Math.round(abandonedRate * 1000) / 1000.0);
    }

    @Transactional(readOnly = true)
    public ForecastResponse forecast() {
        var params = new MapSqlParameterSource()
                .addValue("mid", TenantContext.requireMerchantId());

        List<ForecastItem> items = jdbc.query("""
                select p.id as product_id, p.sku, p.name, inv.quantity,
                       coalesce(sold.units, 0) as units30
                from products p
                join inventory inv on inv.product_id = p.id
                left join (
                    select oi.product_id, sum(oi.quantity) as units
                    from order_items oi
                    join orders o on o.id = oi.order_id
                    where oi.merchant_id = :mid
                      and o.status in ('paid', 'fulfilled')
                      and o.created_at >= now() - interval '30 days'
                    group by oi.product_id
                ) sold on sold.product_id = p.id
                where p.merchant_id = :mid
                order by p.name
                """, params, (rs, n) -> {
            int quantity = rs.getInt("quantity");
            long units30 = rs.getLong("units30");
            double avgDaily = units30 / 30.0;
            Double daysToStockout = avgDaily > 0 ? Math.round((quantity / avgDaily) * 10) / 10.0 : null;
            String status;
            if (quantity <= 0) status = "critical";
            else if (daysToStockout != null && daysToStockout < 7) status = "critical";
            else if (daysToStockout != null && daysToStockout < 14) status = "low";
            else status = "ok";
            return new ForecastItem(
                    rs.getObject("product_id", java.util.UUID.class),
                    rs.getString("sku"), rs.getString("name"),
                    quantity, Math.round(avgDaily * 100) / 100.0, daysToStockout, status);
        });
        return new ForecastResponse(items);
    }

    private Instant defaultStart(String gran, Instant end) {
        return switch (gran) {
            case "week" -> end.minus(84, ChronoUnit.DAYS);   // ~12 weeks
            case "month" -> end.minus(365, ChronoUnit.DAYS);  // ~12 months
            default -> end.minus(30, ChronoUnit.DAYS);
        };
    }
}
