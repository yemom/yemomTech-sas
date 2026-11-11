package com.YemomBizGrow.seo.scoring;

import com.YemomBizGrow.seo.crawler.CrawlExecutionResult;
import com.YemomBizGrow.seo.crawler.PageAnalysisResult;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class SeoScoringEngine {

    public ScoringResult evaluate(CrawlExecutionResult crawl) {
        List<DetectedIssue> issues = new ArrayList<>();

        // Site-wide checks
        if (!crawl.hasRobotsTxt()) {
            issues.add(new DetectedIssue(
                    "TECHNICAL", "INFO",
                    "Missing robots.txt",
                    "A robots.txt file guides search engine spiders on how to crawl your site.",
                    "/robots.txt",
                    "HTTP request returned non-200 status",
                    "Search engines check robots.txt first before indexing your pages.",
                    "Create a robots.txt file at the root of your domain specifying crawl rules and linking your sitemap."
            ));
        }

        if (!crawl.hasSitemap()) {
            issues.add(new DetectedIssue(
                    "TECHNICAL", "WARNING",
                    "Missing XML Sitemap",
                    "No sitemap.xml detected at domain root.",
                    "/sitemap.xml",
                    "HTTP request returned non-200 status",
                    "Sitemaps ensure search engines discover all important landing pages.",
                    "Generate an XML sitemap and place it at /sitemap.xml, or declare its location in robots.txt."
            ));
        }

        Map<String, List<String>> hashToUrls = new HashMap<>();

        for (PageAnalysisResult page : crawl.pages()) {
            // Technical checks
            if (!page.isHttps()) {
                issues.add(new DetectedIssue(
                        "TECHNICAL", "CRITICAL",
                        "Website Not Served Over HTTPS",
                        "Page is served over plain, unencrypted HTTP.",
                        page.url(),
                        "URL protocol is http://",
                        "HTTPS is a confirmed Google search ranking factor and essential for customer trust.",
                        "Install an SSL/TLS certificate (e.g. via Let's Encrypt or Cloudflare) and enforce HTTPS redirect."
                ));
            }

            if (page.statusCode() >= 400) {
                issues.add(new DetectedIssue(
                        "TECHNICAL", "CRITICAL",
                        "Broken Page (HTTP " + page.statusCode() + ")",
                        "Search crawler received an error status code.",
                        page.url(),
                        "HTTP Status: " + page.statusCode(),
                        "Broken URLs waste crawl budget and cause immediate visitor bounce.",
                        "Fix the broken page or redirect the URL to relevant active content using a 301 redirect."
                ));
            }

            if (!page.mixedContentUrls().isEmpty()) {
                issues.add(new DetectedIssue(
                        "TECHNICAL", "WARNING",
                        "Mixed Content Detected",
                        "Page contains resources loaded over insecure HTTP.",
                        page.url(),
                        page.mixedContentUrls().size() + " insecure resources: " + page.mixedContentUrls().get(0),
                        "Modern browsers block passive mixed content and warn users of insecure elements.",
                        "Update all asset references (images, stylesheets, scripts) to use HTTPS or relative paths."
                ));
            }

            // On-Page SEO
            if (page.title() == null || page.title().isBlank()) {
                issues.add(new DetectedIssue(
                        "ON_PAGE", "CRITICAL",
                        "Missing Page Title",
                        "The page lacks a <title> element.",
                        page.url(),
                        "<title> tag is absent or empty",
                        "Title tags are the primary heading displayed in search results and browser tabs.",
                        "Add a unique, descriptive <title> tag between 30 and 60 characters."
                ));
            } else if (page.title().length() < 15 || page.title().length() > 70) {
                issues.add(new DetectedIssue(
                        "ON_PAGE", "WARNING",
                        "Title Tag Length Suboptimal",
                        "Title length is " + page.title().length() + " characters.",
                        page.url(),
                        "Title: \"" + page.title() + "\"",
                        "Titles shorter than 15 chars miss keyword opportunities; titles over 70 chars get truncated in Google SERPs.",
                        "Optimize title tag to be between 30 and 60 characters with your primary keyword."
                ));
            }

            if (page.metaDescription() == null || page.metaDescription().isBlank()) {
                issues.add(new DetectedIssue(
                        "ON_PAGE", "WARNING",
                        "Missing Meta Description",
                        "Page lacks a meta description tag.",
                        page.url(),
                        "<meta name=\"description\"> is missing",
                        "Meta descriptions influence search result click-through rates (CTR).",
                        "Add a compelling meta description between 70 and 155 characters summarizing the page benefit."
                ));
            } else if (page.metaDescription().length() < 50 || page.metaDescription().length() > 165) {
                issues.add(new DetectedIssue(
                        "ON_PAGE", "INFO",
                        "Meta Description Length Warning",
                        "Meta description is " + page.metaDescription().length() + " characters.",
                        page.url(),
                        "Description length: " + page.metaDescription().length(),
                        "Descriptions under 50 characters are brief; descriptions over 160 characters are truncated by search engines.",
                        "Keep meta descriptions between 70 and 155 characters."
                ));
            }

            if (page.h1List().isEmpty()) {
                issues.add(new DetectedIssue(
                        "ON_PAGE", "CRITICAL",
                        "Missing H1 Heading",
                        "Page contains no <h1> heading tag.",
                        page.url(),
                        "Zero <h1> tags found in document",
                        "H1 headings signal the primary topic of the page to search crawlers.",
                        "Add exactly one clear, prominent <h1> heading matching the page's search intent."
                ));
            } else if (page.h1List().size() > 1) {
                issues.add(new DetectedIssue(
                        "ON_PAGE", "WARNING",
                        "Multiple H1 Headings",
                        "Page contains " + page.h1List().size() + " separate <h1> headings.",
                        page.url(),
                        "Found " + page.h1List().size() + " H1 elements",
                        "Having multiple H1 tags can dilute topic clarity.",
                        "Use a single H1 for the page title and structure sub-sections using H2 and H3 elements."
                ));
            }

            if (page.canonicalUrl() == null) {
                issues.add(new DetectedIssue(
                        "ON_PAGE", "INFO",
                        "Missing Canonical URL Tag",
                        "No <link rel=\"canonical\"> tag specified.",
                        page.url(),
                        "No canonical link found in <head>",
                        "Canonical tags prevent duplicate content issues caused by URL query parameters.",
                        "Add a self-referencing canonical tag: <link rel=\"canonical\" href=\"" + page.url() + "\" />"
                ));
            }

            // Content Quality
            if (page.wordCount() < 120 && page.statusCode() == 200) {
                issues.add(new DetectedIssue(
                        "CONTENT", "WARNING",
                        "Thin Content",
                        "Page has only " + page.wordCount() + " words of visible text.",
                        page.url(),
                        "Word count: " + page.wordCount(),
                        "Search engines favor in-depth, helpful content over pages with minimal substance.",
                        "Expand page content with valuable details, FAQs, or customer information."
                ));
            }

            if (page.contentHash() != null) {
                hashToUrls.computeIfAbsent(page.contentHash(), k -> new ArrayList<>()).add(page.url());
            }

            // Accessibility & Mobile
            if (!page.hasViewportMeta()) {
                issues.add(new DetectedIssue(
                        "ACCESSIBILITY", "CRITICAL",
                        "Missing Mobile Viewport Tag",
                        "No <meta name=\"viewport\"> tag found.",
                        page.url(),
                        "Viewport meta tag missing in <head>",
                        "Mobile-friendly rendering is mandatory for modern search indexing and usability.",
                        "Add <meta name=\"viewport\" content=\"width=device-width, initial-scale=1\"> inside <head>."
                ));
            }

            if (page.missingAltImagesCount() > 0) {
                issues.add(new DetectedIssue(
                        "ACCESSIBILITY", "WARNING",
                        "Images Missing Alt Attributes",
                        page.missingAltImagesCount() + " out of " + page.imagesCount() + " images lack descriptive alt text.",
                        page.url(),
                        page.missingAltImagesCount() + " images missing alt text",
                        "Alt text is crucial for visually impaired screen readers and Google Image search discovery.",
                        "Add meaningful alt=\"...\" descriptions to all informational images."
                ));
            }

            // Performance signals
            if (page.loadTimeMs() > 2500) {
                issues.add(new DetectedIssue(
                        "PERFORMANCE", "WARNING",
                        "Slow Server Response Time (" + page.loadTimeMs() + "ms)",
                        "Initial page HTML response took longer than 2.5 seconds.",
                        page.url(),
                        "Load time: " + page.loadTimeMs() + "ms",
                        "Slow page response harms Core Web Vitals and increases mobile abandonment.",
                        "Optimize database queries, enable server-side caching, or leverage a CDN."
                ));
            }
        }

        // Duplicate content detection across crawled pages
        for (Map.Entry<String, List<String>> entry : hashToUrls.entrySet()) {
            if (entry.getValue().size() > 1) {
                issues.add(new DetectedIssue(
                        "CONTENT", "WARNING",
                        "Duplicate Page Content",
                        entry.getValue().size() + " pages have identical text content.",
                        entry.getValue().get(0),
                        "Identical content across: " + String.join(", ", entry.getValue()),
                        "Duplicate pages split link equity and confuse search crawlers about which URL to rank.",
                        "Consolidate duplicate pages, use 301 redirects, or set canonical tags pointing to the master URL."
                ));
            }
        }

        // Compute scores
        int criticalCount = 0;
        int warningCount = 0;
        int infoCount = 0;

        int techPen = 0;
        int onPagePen = 0;
        int contentPen = 0;
        int accessPen = 0;

        for (DetectedIssue issue : issues) {
            int pen = switch (issue.severity()) {
                case "CRITICAL" -> {
                    criticalCount++;
                    yield 20;
                }
                case "WARNING" -> {
                    warningCount++;
                    yield 8;
                }
                default -> {
                    infoCount++;
                    yield 2;
                }
            };

            switch (issue.category()) {
                case "TECHNICAL", "PERFORMANCE" -> techPen += pen;
                case "ON_PAGE" -> onPagePen += pen;
                case "CONTENT" -> contentPen += pen;
                case "ACCESSIBILITY" -> accessPen += pen;
            }
        }

        int techScore = Math.max(10, Math.min(100, 100 - techPen));
        int onPageScore = Math.max(10, Math.min(100, 100 - onPagePen));
        int contentScore = Math.max(10, Math.min(100, 100 - contentPen));
        int accessScore = Math.max(10, Math.min(100, 100 - accessPen));

        int overall = (int) Math.round((techScore * 0.3) + (onPageScore * 0.3) + (contentScore * 0.2) + (accessScore * 0.2));

        return new ScoringResult(
                overall,
                techScore,
                onPageScore,
                contentScore,
                accessScore,
                issues,
                criticalCount,
                warningCount,
                infoCount
        );
    }
}
