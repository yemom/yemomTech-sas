package com.YemomBizGrow.seo.crawler;

import java.util.List;

public record CrawlExecutionResult(
        List<PageAnalysisResult> pages,
        boolean hasRobotsTxt,
        boolean hasSitemapXml,
        long crawlDurationMs
) {
    public boolean hasSitemap() {
        return hasSitemapXml;
    }
}
