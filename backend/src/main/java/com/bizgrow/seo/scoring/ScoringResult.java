package com.YemomBizGrow.seo.scoring;

import java.util.List;

public record ScoringResult(
        int overallScore,
        int technicalScore,
        int onPageScore,
        int contentScore,
        int accessibilityScore,
        List<DetectedIssue> issues,
        int criticalCount,
        int warningCount,
        int infoCount
) {}
