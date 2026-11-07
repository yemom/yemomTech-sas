package com.YemomBizGrow.seo.crawler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;

/**
 * SSRF-safe, bounded breadth-first crawler for website audits.
 */
@Component
public class WebsiteCrawler {

    private static final Logger log = LoggerFactory.getLogger(WebsiteCrawler.class);

    private static final int MAX_PAGES = 30; // Max pages analyzed per audit
    private static final int MAX_DEPTH = 3;

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

        queue.add(new CrawlNode(startUri, 0));
        visited.add(normalize(startUri));

        while (!queue.isEmpty() && analyzedPages.size() < MAX_PAGES) {
            CrawlNode node = queue.poll();
            URI currentUri = node.uri;

            SafeFetchResult fetchResult = safeHttpClient.fetch(currentUri);
            if (!fetchResult.success() && fetchResult.statusCode() == 0) {
                // Fetch failed (network/SSRF)
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

        // Technical probes: /robots.txt and /sitemap.xml
        boolean hasRobotsTxt = checkResourceExists(startUri.resolve("/robots.txt"));
        boolean hasSitemap = checkResourceExists(startUri.resolve("/sitemap.xml"));

        long duration = System.currentTimeMillis() - startTime;
        return new CrawlExecutionResult(analyzedPages, hasRobotsTxt, hasSitemap, duration);
    }

    private boolean checkResourceExists(URI uri) {
        try {
            SafeFetchResult res = safeHttpClient.fetch(uri);
            return res.success() && res.statusCode() >= 200 && res.statusCode() < 300;
        } catch (Exception e) {
            return false;
        }
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
        return s;
    }

    private record CrawlNode(URI uri, int depth) {}
}
