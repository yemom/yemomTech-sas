package com.YemomBizGrow.seo.scoring;

public record DetectedIssue(
        String category,
        String severity,
        String title,
        String description,
        String affectedUrl,
        String evidence,
        String explanation,
        String recommendation
) {}
