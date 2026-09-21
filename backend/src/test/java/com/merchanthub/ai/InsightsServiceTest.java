package com.merchanthub.ai;

import com.merchanthub.dto.AnalyticsDtos.ForecastItem;
import com.merchanthub.dto.AnalyticsDtos.ForecastResponse;
import com.merchanthub.dto.AnalyticsDtos.PeriodTotals;
import com.merchanthub.dto.AnalyticsDtos.RevenueResponse;
import com.merchanthub.dto.InsightsDtos.DailySummaryResponse;
import com.merchanthub.service.AnalyticsService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class InsightsServiceTest {

    private final AnalyticsService analytics = mock(AnalyticsService.class);
    private final AnthropicClient anthropic = mock(AnthropicClient.class);
    private final InsightsService insights = new InsightsService(analytics, anthropic);

    private static RevenueResponse revenue() {
        return new RevenueResponse("day", List.of(), new BigDecimal("1000.00"), 12,
                new PeriodTotals(new BigDecimal("800.00"), 9), 25.0);
    }

    private static ForecastResponse forecastWithOneCritical() {
        return new ForecastResponse(List.of(
                new ForecastItem(java.util.UUID.randomUUID(), "SKU-1", "Widget", 2, 1.5, 1.3, "critical"),
                new ForecastItem(java.util.UUID.randomUUID(), "SKU-2", "Gizmo", 100, 1.0, 100.0, "ok")));
    }

    @Test
    void fallsBackCleanlyWhenNoApiKeyIsConfigured() {
        when(analytics.revenue("day", null, null)).thenReturn(revenue());
        when(analytics.forecast()).thenReturn(forecastWithOneCritical());
        when(anthropic.isConfigured()).thenReturn(false);

        DailySummaryResponse result = insights.dailySummary();

        assertThat(result.aiAvailable()).isFalse();
        assertThat(result.summary()).contains("ANTHROPIC_API_KEY");
        // Even with AI disabled, the real numbers are still returned.
        assertThat(result.groundedOnRevenue().totalOrders()).isEqualTo(12);
        assertThat(result.groundedOnForecast().items()).hasSize(2);
        verify(anthropic, never()).complete(any(), any(), anyInt());
    }

    @Test
    void returnsTheModelTextWhenConfigured() {
        when(analytics.revenue("day", null, null)).thenReturn(revenue());
        when(analytics.forecast()).thenReturn(forecastWithOneCritical());
        when(anthropic.isConfigured()).thenReturn(true);
        when(anthropic.complete(any(), any(), anyInt())).thenReturn("Revenue is up 25%. Widget is critically low.");

        DailySummaryResponse result = insights.dailySummary();

        assertThat(result.aiAvailable()).isTrue();
        assertThat(result.summary()).isEqualTo("Revenue is up 25%. Widget is critically low.");
    }

    @Test
    void degradesToUnavailableIfTheApiCallThrows() {
        when(analytics.revenue("day", null, null)).thenReturn(revenue());
        when(analytics.forecast()).thenReturn(forecastWithOneCritical());
        when(anthropic.isConfigured()).thenReturn(true);
        when(anthropic.complete(any(), any(), anyInt())).thenThrow(new RuntimeException("rate limited"));

        DailySummaryResponse result = insights.dailySummary();

        assertThat(result.aiAvailable()).isFalse();
        assertThat(result.summary()).contains("rate limited");
    }
}
