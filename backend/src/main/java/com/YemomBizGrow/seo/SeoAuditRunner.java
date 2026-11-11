package com.YemomBizGrow.seo;

import com.YemomBizGrow.seo.crawler.CrawlExecutionResult;
import com.YemomBizGrow.seo.crawler.PageAnalysisResult;
import com.YemomBizGrow.seo.crawler.WebsiteCrawler;
import com.YemomBizGrow.seo.scoring.DetectedIssue;
import com.YemomBizGrow.seo.scoring.ScoringResult;
import com.YemomBizGrow.seo.scoring.SeoScoringEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.time.Instant;
import java.util.UUID;

@Component
public class SeoAuditRunner {

    private static final Logger log = LoggerFactory.getLogger(SeoAuditRunner.class);

    private final SeoAuditRepository auditRepository;
    private final SeoCrawledPageRepository crawledPageRepository;
    private final SeoIssueRepository issueRepository;
    private final WebsiteCrawler websiteCrawler;
    private final SeoScoringEngine scoringEngine;

    public SeoAuditRunner(
            SeoAuditRepository auditRepository,
            SeoCrawledPageRepository crawledPageRepository,
            SeoIssueRepository issueRepository,
            WebsiteCrawler websiteCrawler,
            SeoScoringEngine scoringEngine) {
        this.auditRepository = auditRepository;
        this.crawledPageRepository = crawledPageRepository;
        this.issueRepository = issueRepository;
        this.websiteCrawler = websiteCrawler;
        this.scoringEngine = scoringEngine;
    }

    @Async
    public void executeAuditAsync(UUID auditId, String websiteUrl) {
        SeoAudit audit = auditRepository.findById(auditId).orElse(null);
        if (audit == null) {
            log.warn("Cannot run SEO audit; audit record {} not found", auditId);
            return;
        }

        audit.setStatus(SeoAuditStatus.RUNNING);
        audit.setStartedAt(Instant.now());
        auditRepository.save(audit);

        try {
            URI startUri = URI.create(websiteUrl);
            CrawlExecutionResult crawlResult = websiteCrawler.crawl(startUri);
            ScoringResult scoringResult = scoringEngine.evaluate(crawlResult);

            // Persist crawled pages
            for (PageAnalysisResult page : crawlResult.pages()) {
                SeoCrawledPage crawled = new SeoCrawledPage();
                crawled.setAuditId(auditId);
                crawled.setOrganizationId(audit.getOrganizationId());
                crawled.setWebsiteId(audit.getWebsiteId());
                crawled.setUrl(page.url());
                crawled.setStatusCode(page.statusCode());
                crawled.setTitle(page.title());
                crawled.setMetaDescription(page.metaDescription());
                crawled.setCanonicalUrl(page.canonicalUrl());
                crawled.setH1Count(page.h1List().size());
                crawled.setH2Count(page.h2Count());
                crawled.setWordCount(page.wordCount());
                crawled.setInternalLinksCount(page.internalLinks().size());
                crawled.setExternalLinksCount(page.externalLinks().size());
                crawled.setImagesCount(page.imagesCount());
                crawled.setMissingAltImagesCount(page.missingAltImagesCount());
                crawled.setLoadTimeMs(page.loadTimeMs());
                crawled.setHasViewportMeta(page.hasViewportMeta());
                crawled.setHttps(page.isHttps());
                crawled.setContentHash(page.contentHash());
                crawledPageRepository.save(crawled);
            }

            // Persist issues
            for (DetectedIssue issue : scoringResult.issues()) {
                SeoIssue entity = new SeoIssue();
                entity.setAuditId(auditId);
                entity.setOrganizationId(audit.getOrganizationId());
                entity.setWebsiteId(audit.getWebsiteId());
                entity.setCategory(issue.category());
                entity.setSeverity(issue.severity());
                entity.setTitle(issue.title());
                entity.setDescription(issue.description());
                entity.setAffectedUrl(issue.affectedUrl());
                entity.setEvidence(issue.evidence());
                entity.setExplanation(issue.explanation());
                entity.setRecommendation(issue.recommendation());
                entity.setStatus("OPEN");
                issueRepository.save(entity);
            }

            // Update audit with final results
            audit.setScore(scoringResult.overallScore());
            audit.setTechnicalScore(scoringResult.technicalScore());
            audit.setOnPageScore(scoringResult.onPageScore());
            audit.setContentScore(scoringResult.contentScore());
            audit.setAccessibilityScore(scoringResult.accessibilityScore());
            audit.setPagesCrawled(crawlResult.pages().size());
            audit.setCriticalIssuesCount(scoringResult.criticalCount());
            audit.setWarningIssuesCount(scoringResult.warningCount());
            audit.setInfoIssuesCount(scoringResult.infoCount());
            audit.setStatus(SeoAuditStatus.COMPLETED);
            audit.setCompletedAt(Instant.now());
            auditRepository.save(audit);

            log.info("SEO audit {} completed with score {}", auditId, scoringResult.overallScore());
        } catch (Exception e) {
            log.error("SEO audit {} failed: {}", auditId, e.getMessage(), e);
            audit.setStatus(SeoAuditStatus.FAILED);
            audit.setErrorMessage(e.getMessage() != null ? e.getMessage().substring(0, Math.min(e.getMessage().length(), 950)) : "Unexpected error during crawl");
            audit.setCompletedAt(Instant.now());
            auditRepository.save(audit);
        }
    }
}
