package com.YemomBizGrow.seo.crawler;

import java.util.List;

public record PageAnalysisResult(
        String url,
        int statusCode,
        String title,
        String metaDescription,
        String canonicalUrl,
        String robots,
        List<String> h1Tags,
        int h2Count,
        int h3Count,
        int wordCount,
        List<String> internalLinks,
        List<String> externalLinks,
        int imagesCount,
        int missingAltImagesCount,
        boolean hasViewportMeta,
        boolean isHttps,
        List<String> insecureResources,
        String contentHash,
        long loadTimeMs
) {
    public List<String> h1List() {
        return h1Tags != null ? h1Tags : List.of();
    }

    public List<String> mixedContentUrls() {
        return insecureResources != null ? insecureResources : List.of();
    }
}
