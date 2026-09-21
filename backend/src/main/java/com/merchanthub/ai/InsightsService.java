package com.merchanthub.ai;

import com.merchanthub.dto.AnalyticsDtos.ForecastItem;
import com.merchanthub.dto.AnalyticsDtos.ForecastResponse;
import com.merchanthub.dto.AnalyticsDtos.RevenueResponse;
import com.merchanthub.dto.InsightsDtos.DailySummaryResponse;
import com.merchanthub.service.AnalyticsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Turns the merchant's own revenue and stockout-forecast numbers (computed by
 * {@link AnalyticsService}, straight from Postgres) into a short natural-
 * language summary. The LLM never sees anything beyond those two responses —
 * every claim in the summary is traceable back to {@code groundedOnRevenue}/
 * {@code groundedOnForecast} in the response, the same source-grounding
 * pattern as the RAG assistant, applied to structured analytics instead of
 * retrieved documents.
 */
@Service
public class InsightsService {

    private static final Logger log = LoggerFactory.getLogger(InsightsService.class);

    private static final String SYSTEM_PROMPT = """
            You are a concise merchandising assistant for a small e-commerce seller.
            You will be given today's revenue numbers and a per-product stockout
            forecast, both computed directly from the merchant's own order and
            inventory data. Write a 3-5 sentence plain-English summary a busy
            merchant could read in ten seconds: call out the revenue trend, and
            name any specific products that are 'critical' or 'low' with their
            days-to-stockout. Do not invent numbers or products that were not
            given to you. No markdown, no headers — plain prose.
            """;

    private final AnalyticsService analytics;
    private final AnthropicClient anthropic;

    public InsightsService(AnalyticsService analytics, AnthropicClient anthropic) {
        this.analytics = analytics;
        this.anthropic = anthropic;
    }

    public DailySummaryResponse dailySummary() {
        RevenueResponse revenue = analytics.revenue("day", null, null);
        ForecastResponse forecast = analytics.forecast();

        if (!anthropic.isConfigured()) {
            return new DailySummaryResponse(false,
                    "AI summary unavailable — set ANTHROPIC_API_KEY to enable it.",
                    revenue, forecast);
        }

        try {
            String prompt = buildPrompt(revenue, forecast);
            String summary = anthropic.complete(SYSTEM_PROMPT, prompt, 300);
            return new DailySummaryResponse(true, summary, revenue, forecast);
        } catch (Exception e) {
            log.warn("Falling back to unavailable daily summary: {}", e.getMessage());
            return new DailySummaryResponse(false,
                    "AI summary temporarily unavailable: " + e.getMessage(),
                    revenue, forecast);
        }
    }

    private String buildPrompt(RevenueResponse revenue, ForecastResponse forecast) {
        StringBuilder sb = new StringBuilder();
        sb.append("Revenue (last 30 days, daily buckets): total=")
                .append(revenue.totalRevenue()).append(", orders=").append(revenue.totalOrders())
                .append(", change vs prior period=")
                .append(revenue.changePct() == null ? "n/a" : revenue.changePct() + "%")
                .append(".\n\n");

        List<ForecastItem> risky = forecast.items().stream()
                .filter(i -> !"ok".equals(i.status()))
                .toList();

        if (risky.isEmpty()) {
            sb.append("Stockout forecast: no products are currently low or critical.");
        } else {
            sb.append("Stockout forecast (only non-ok items):\n");
            for (ForecastItem i : risky) {
                sb.append("- ").append(i.name()).append(" (SKU ").append(i.sku()).append("): ")
                        .append(i.quantity()).append(" units on hand, status=").append(i.status())
                        .append(i.daysToStockout() == null ? "" : ", ~" + i.daysToStockout() + " days to stockout")
                        .append("\n");
            }
        }
        return sb.toString();
    }
}
