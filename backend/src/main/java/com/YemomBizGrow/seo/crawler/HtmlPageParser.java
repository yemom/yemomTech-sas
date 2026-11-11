package com.YemomBizGrow.seo.crawler;

import com.YemomBizGrow.common.util.TokenHasher;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Component
public class HtmlPageParser {

    public PageAnalysisResult parse(URI pageUri, int statusCode, String html, long loadTimeMs) {
        if (html == null || html.isBlank()) {
            return new PageAnalysisResult(
                    pageUri.toString(),
                    statusCode,
                    null, null, null, null,
                    List.of(), 0, 0, 0,
                    List.of(), List.of(), 0, 0,
                    false, "https".equalsIgnoreCase(pageUri.getScheme()),
                    List.of(), null, loadTimeMs
            );
        }

        Document doc = Jsoup.parse(html, pageUri.toString());
        boolean isHttps = "https".equalsIgnoreCase(pageUri.getScheme());

        // 1. Title & Meta
        String title = doc.title().isBlank() ? null : doc.title().trim();
        Element metaDesc = doc.selectFirst("meta[name=description i]");
        String metaDescription = (metaDesc != null && metaDesc.hasAttr("content")) ? metaDesc.attr("content").trim() : null;

        Element canonical = doc.selectFirst("link[rel=canonical i]");
        String canonicalUrl = (canonical != null && canonical.hasAttr("href")) ? canonical.attr("abs:href") : null;

        Element metaRobots = doc.selectFirst("meta[name=robots i]");
        String robots = (metaRobots != null && metaRobots.hasAttr("content")) ? metaRobots.attr("content").trim() : null;

        // 2. Viewport
        Element viewport = doc.selectFirst("meta[name=viewport i]");
        boolean hasViewport = viewport != null && viewport.hasAttr("content");

        // 3. Headings
        List<String> h1List = new ArrayList<>();
        for (Element h1 : doc.select("h1")) {
            String text = h1.text().trim();
            if (!text.isEmpty()) {
                h1List.add(text);
            }
        }
        int h2Count = doc.select("h2").size();
        int h3Count = doc.select("h3").size();

        // 4. Links
        List<String> internalLinks = new ArrayList<>();
        List<String> externalLinks = new ArrayList<>();
        String currentHost = pageUri.getHost() != null ? pageUri.getHost().toLowerCase(Locale.ROOT) : "";

        Elements links = doc.select("a[href]");
        for (Element a : links) {
            String absUrl = a.attr("abs:href");
            if (absUrl.isBlank()) continue;
            try {
                URI linkUri = URI.create(absUrl);
                String scheme = linkUri.getScheme();
                if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
                    continue;
                }
                String linkHost = linkUri.getHost() != null ? linkUri.getHost().toLowerCase(Locale.ROOT) : "";
                if (linkHost.equals(currentHost) || linkHost.endsWith("." + currentHost)) {
                    internalLinks.add(absUrl);
                } else {
                    externalLinks.add(absUrl);
                }
            } catch (Exception ignored) {
                // Ignore malformed hrefs
            }
        }

        // 5. Images
        Elements images = doc.select("img");
        int imagesCount = images.size();
        int missingAlt = 0;
        for (Element img : images) {
            String alt = img.attr("alt");
            if (alt == null || alt.trim().isEmpty()) {
                missingAlt++;
            }
        }

        // 6. Mixed content (HTTP resources on HTTPS page)
        List<String> mixedContent = new ArrayList<>();
        if (isHttps) {
            for (Element el : doc.select("img[src], script[src], link[rel=stylesheet][href], iframe[src]")) {
                String src = el.hasAttr("src") ? el.attr("src") : el.attr("href");
                if (src != null && src.toLowerCase(Locale.ROOT).startsWith("http://")) {
                    mixedContent.add(src);
                }
            }
        }

        // 7. Word count & Content hash
        String text = doc.body() != null ? doc.body().text() : "";
        String[] words = text.isBlank() ? new String[0] : text.trim().split("\\s+");
        int wordCount = words.length;
        String contentHash = text.isBlank() ? null : TokenHasher.sha256Hex(text.trim());

        return new PageAnalysisResult(
                pageUri.toString(),
                statusCode,
                title,
                metaDescription,
                canonicalUrl,
                robots,
                h1List,
                h2Count,
                h3Count,
                wordCount,
                internalLinks,
                externalLinks,
                imagesCount,
                missingAlt,
                hasViewport,
                isHttps,
                mixedContent,
                contentHash,
                loadTimeMs
        );
    }
}
