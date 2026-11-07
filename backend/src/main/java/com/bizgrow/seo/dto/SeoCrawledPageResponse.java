package com.YemomBizGrow.seo.dto;

import com.YemomBizGrow.seo.SeoCrawledPage;

import java.time.Instant;
import java.util.UUID;

public record SeoCrawledPageResponse(
        UUID id,
        UUID auditId,
        UUID organizationId,
        UUID websiteId,
        String url,
        Integer statusCode,
        String title,
        String metaDescription,
        String canonicalUrl,
        int h1Count,
        int h2Count,
        int wordCount,
        int internalLinksCount,
        int externalLinksCount,
        int imagesCount,
        int missingAltImagesCount,
        Long loadTimeMs,
        boolean hasViewportMeta,
        boolean isHttps,
        Instant createdAt
) {
    public static SeoCrawledPageResponse from(SeoCrawledPage page) {
        return new SeoCrawledPageResponse(
                page.getId(),
                page.getAuditId(),
                page.getOrganizationId(),
                page.getWebsiteId(),
                page.getUrl(),
                page.getStatusCode(),
                page.getTitle(),
                page.getMetaDescription(),
                page.getCanonicalUrl(),
                page.getH1Count(),
                page.getH2Count(),
                page.getWordCount(),
                page.getInternalLinksCount(),
                page.getExternalLinksCount(),
                page.getImagesCount(),
                page.getMissingAltImagesCount(),
                page.getLoadTimeMs(),
                page.isHasViewportMeta(),
                page.isHttps(),
                page.getCreatedAt()
        );
    }
}
