package com.YemomBizGrow.seo;

import com.YemomBizGrow.seo.crawler.CrawlExecutionResult;
import com.YemomBizGrow.seo.crawler.PageAnalysisResult;
import com.YemomBizGrow.seo.scoring.ScoringResult;
import com.YemomBizGrow.seo.scoring.SeoScoringEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SeoScoringEngineTest {

    private SeoScoringEngine scoringEngine;

    @BeforeEach
    void setUp() {
        scoringEngine = new SeoScoringEngine();
    }

    @Test
    @DisplayName("Gives high score when all SEO checks pass")
    void perfectSiteYieldsHighScore() {
        PageAnalysisResult page = new PageAnalysisResult(
                "https://example.com",
                200,
                "Example Domain - Leading Growth Platform",
                "This is a high quality meta description for the example domain.",
                "https://example.com",
                "index, follow",
                List.of("Main Page Headline"),
                3,
                2,
                350,
                List.of("https://example.com/about"),
                List.of("https://external.com"),
                2,
                0,
                true,
                true,
                List.of(),
                "hash123",
                250
        );

        CrawlExecutionResult crawl = new CrawlExecutionResult(
                List.of(page),
                true,
                true,
                500
        );

        ScoringResult result = scoringEngine.evaluate(crawl);

        assertThat(result.overallScore()).isEqualTo(100);
        assertThat(result.criticalCount()).isEqualTo(0);
        assertThat(result.warningCount()).isEqualTo(0);
        assertThat(result.issues()).isEmpty();
    }

    @Test
    @DisplayName("Penalizes non-HTTPS, missing title, and missing H1")
    void penalizesCriticalIssues() {
        PageAnalysisResult page = new PageAnalysisResult(
                "http://example.com",
                200,
                null, // Missing title -> CRITICAL
                null,
                null,
                null,
                List.of(), // Missing H1 -> CRITICAL
                0,
                0,
                50, // Low word count -> WARNING
                List.of(),
                List.of(),
                3,
                3, // Missing alt -> WARNING
                false, // Missing viewport -> WARNING
                false, // Non-HTTPS -> CRITICAL
                List.of(),
                "hash456",
                300
        );

        CrawlExecutionResult crawl = new CrawlExecutionResult(
                List.of(page),
                false, // Missing robots -> INFO
                false, // Missing sitemap -> WARNING
                800
        );

        ScoringResult result = scoringEngine.evaluate(crawl);

        assertThat(result.overallScore()).isLessThan(80);
        assertThat(result.criticalCount()).isGreaterThanOrEqualTo(3);
        assertThat(result.warningCount()).isGreaterThanOrEqualTo(3);
        assertThat(result.infoCount()).isGreaterThanOrEqualTo(1);
    }
}
