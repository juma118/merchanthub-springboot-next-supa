package com.merchanthub.dto;

import com.merchanthub.dto.AnalyticsDtos.ForecastResponse;
import com.merchanthub.dto.AnalyticsDtos.RevenueResponse;

public final class InsightsDtos {
    private InsightsDtos() {}

    /**
     * {@code groundedOn} carries the exact numbers the summary was generated
     * from, so a merchant (or a reviewer) can check the AI text against the
     * real data it's describing rather than trusting it blind.
     */
    public record DailySummaryResponse(
            boolean aiAvailable,
            String summary,
            RevenueResponse groundedOnRevenue,
            ForecastResponse groundedOnForecast) {}
}
