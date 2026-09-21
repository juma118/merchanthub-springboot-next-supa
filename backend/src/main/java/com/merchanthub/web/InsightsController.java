package com.merchanthub.web;

import com.merchanthub.ai.InsightsService;
import com.merchanthub.dto.InsightsDtos.DailySummaryResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/insights")
public class InsightsController {

    private final InsightsService insights;

    public InsightsController(InsightsService insights) {
        this.insights = insights;
    }

    @GetMapping("/daily-summary")
    public DailySummaryResponse dailySummary() {
        return insights.dailySummary();
    }
}
