package com.merchanthub.web;

import com.merchanthub.dto.AnalyticsDtos.*;
import com.merchanthub.service.AnalyticsService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/analytics")
public class AnalyticsController {

    private final AnalyticsService analytics;

    public AnalyticsController(AnalyticsService analytics) {
        this.analytics = analytics;
    }

    @GetMapping("/revenue")
    public RevenueResponse revenue(
            @RequestParam(defaultValue = "day") String granularity,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        return analytics.revenue(granularity, WebParams.parseInstant(from), WebParams.parseInstant(to));
    }

    @GetMapping("/top-products")
    public TopProductsResponse topProducts(
            @RequestParam(defaultValue = "revenue") String metric,
            @RequestParam(defaultValue = "5") int limit) {
        return analytics.topProducts(metric, limit);
    }

    @GetMapping("/funnel")
    public FunnelResponse funnel(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        return analytics.funnel(WebParams.parseInstant(from), WebParams.parseInstant(to));
    }

    @GetMapping("/forecast")
    public ForecastResponse forecast() {
        return analytics.forecast();
    }
}
