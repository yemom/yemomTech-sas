package com.YemomBizGrow.integration.google.dto;

import java.time.LocalDate;

public record AnalyticsDailyPoint(
        LocalDate date,
        long sessions,
        long users,
        long pageViews,
        long clicks,
        long impressions
) {}
