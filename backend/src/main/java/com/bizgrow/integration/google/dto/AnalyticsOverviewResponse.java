package com.YemomBizGrow.integration.google.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public record AnalyticsOverviewResponse(
        long totalSessions,
        long totalUsers,
        long totalPageViews,
        double avgBounceRate,
        long totalClicks,
        long totalImpressions,
        double avgCtr,
        double avgPosition,
        List<AnalyticsDailyPoint> dailyMetrics,
        List<Map<String, Object>> topPages,
        List<Map<String, Object>> topQueries,
        List<Map<String, Object>> devices,
        List<Map<String, Object>> channels
) {}
