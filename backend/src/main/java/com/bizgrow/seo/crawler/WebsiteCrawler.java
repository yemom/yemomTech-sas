package com.YemomBizGrow.seo.crawler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * SSRF-safe, bounded breadth-first crawler for website audits.
 *
 * <p>Discovers and analyzes pages, parses robots.txt and sitemap.xml,
 * and tracks broken internal links as actionable issues.</p>
 */
@Component
public class WebsiteCrawler {

    private static final Logger log = LoggerFactory.getLogger(WebsiteCrawler.class);

    private static final int MAX_PAGES = 30; // Max pages analyzed per audit
    private static final int MAX_DEPTH = 3;
    private static final Pattern SITEMAP_LOC_PATTERN = Pattern.compile("<loc>\\s*(https?://[^<\\s]+)\\s*</loc>", Pattern.CASE_INSENSITIVE);
    private static final Pattern ROBOTS_SITEMAP_PATTERN = Pattern.compile("^\\s*Sitemap:\\s*(https?://\\S+)", Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);

    private final SafeHttpClient safeHttpClient;
    private final HtmlPageParser htmlParser;

    public WebsiteCrawler(SafeHttpClient safeHttpClient, HtmlPageParser htmlParser) {
        this.safeHttpClient = safeHttpClient;
        this.htmlParser = htmlParser;
    }

    public CrawlExecutionResult crawl(URI startUri) {
        long startTime = System.currentTimeMillis();
        List<PageAnalysisResult> analyzedPages = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        Queue<CrawlNode> queue = new ArrayDeque<>();

        // 1. Technical probes: robots.txt and sitemap.xml
        String robotsSitemapUrl = null;
        boolean hasRobotsTxt = false;
        try {
            SafeFetchResult robotsRes = safeHttpClient.fetch(startUri.resolve("/robots.txt"));
            if (robotsRes.success() && robotsRes.statusCode() >= 200 && robotsRes.statusCode() < 300 && robotsRes.body() != null) {
                hasRobotsTxt = true;
                Matcher sitemapMatcher = ROBOTS_SITEMAP_PATTERN.matcher(robotsRes.body());
                if (sitemapMatcher.find()) {
                    robotsSitemapUrl = sitemapMatcher.group(1).trim();
                }
            }
        } catch (Exception e) {
            log.debug("Error probing /robots.txt for {}: {}", startUri, e.getMessage());
        }

        boolean hasSitemap = false;
        List<String> sitemapUrls = new ArrayList<>();
        URI sitemapUri = (robotsSitemapUrl != null) ? URI.create(robotsSitemapUrl) : startUri.resolve("/sitemap.xml");
        try {
            SafeFetchResult sitemapRes = safeHttpClient.fetch(sitemapUri);
            if (sitemapRes.success() && sitemapRes.statusCode() >= 200 && sitemapRes.statusCode() < 300 && sitemapRes.body() != null) {
                hasSitemap = true;
                Matcher locMatcher = SITEMAP_LOC_PATTERN.matcher(sitemapRes.body());
                while (locMatcher.find() && sitemapUrls.size() < MAX_PAGES) {
                    sitemapUrls.add(locMatcher.group(1).trim());
                }
            }
        } catch (Exception e) {
            log.debug("Error probing sitemap for {}: {}", startUri, e.getMessage());
        }

        // 2. Initialize crawl queue with startUri and sitemap URLs
        queue.add(new CrawlNode(startUri, 0));
        visited.add(normalize(startUri));

        for (String url : sitemapUrls) {
            try {
                URI uri = URI.create(url);
                if (isSameDomain(startUri, uri)) {
                    String norm = normalize(uri);
                    if (!visited.contains(norm) && visited.size() < MAX_PAGES) {
                        visited.add(norm);
                        queue.add(new CrawlNode(uri, 1));
                    }
                }
            } catch (Exception ignored) {
            }
        }

        // 3. BFS crawl loop
        while (!queue.isEmpty() && analyzedPages.size() < MAX_PAGES) {
            CrawlNode node = queue.poll();
            URI currentUri = node.uri;

            SafeFetchResult fetchResult = safeHttpClient.fetch(currentUri);
            if (!fetchResult.success() && fetchResult.statusCode() == 0) {
                // Fetch failed (network/SSRF) - record as broken page issue
                PageAnalysisResult brokenPage = new PageAnalysisResult(
                        currentUri.toString(),
                        fetchResult.statusCode() > 0 ? fetchResult.statusCode() : 502,
                        null, null, null, null,
                        Collections.emptyList(), 0, 0, 0,
                        Collections.emptyList(), Collections.emptyList(),
                        0, 0, false,
                        "https".equalsIgnoreCase(currentUri.getScheme()),
                        Collections.emptyList(), "", fetchResult.loadTimeMs()
                );
                analyzedPages.add(brokenPage);
                continue;
            }

            PageAnalysisResult pageResult = htmlParser.parse(
                    fetchResult.finalUri() != null ? fetchResult.finalUri() : currentUri,
                    fetchResult.statusCode(),
                    fetchResult.body(),
                    fetchResult.loadTimeMs()
            );
            analyzedPages.add(pageResult);

            if (node.depth < MAX_DEPTH) {
                for (String linkStr : pageResult.internalLinks()) {
                    try {
                        URI linkUri = URI.create(linkStr);
                        String norm = normalize(linkUri);
                        if (!visited.contains(norm) && visited.size() < MAX_PAGES * 3) {
                            visited.add(norm);
                            queue.add(new CrawlNode(linkUri, node.depth + 1));
                        }
                    } catch (Exception ignored) {
                    }
                }
            }
        }

        long duration = System.currentTimeMillis() - startTime;
        return new CrawlExecutionResult(analyzedPages, hasRobotsTxt, hasSitemap, duration);
    }

    private boolean isSameDomain(URI base, URI target) {
        if (base.getHost() == null || target.getHost() == null) return false;
        String h1 = base.getHost().toLowerCase();
        String h2 = target.getHost().toLowerCase();
        if (h1.startsWith("www.")) h1 = h1.substring(4);
        if (h2.startsWith("www.")) h2 = h2.substring(4);
        return h1.equals(h2);
    }

    private String normalize(URI uri) {
        String s = uri.normalize().toString();
        int hashIdx = s.indexOf('#');
        if (hashIdx >= 0) {
            s = s.substring(0, hashIdx);
        }
        if (s.endsWith("/")) {
            s = s.substring(0, s.length() - 1);
        }
        return s.toLowerCase();
    }

    private record CrawlNode(URI uri, int depth) {}
}
