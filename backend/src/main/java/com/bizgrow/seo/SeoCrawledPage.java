package com.YemomBizGrow.seo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "seo_crawled_pages")
@Getter
@Setter
@NoArgsConstructor
public class SeoCrawledPage {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "audit_id", nullable = false)
    private UUID auditId;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "website_id", nullable = false)
    private UUID websiteId;

    @Column(name = "url", nullable = false, length = 2048)
    private String url;

    @Column(name = "status_code")
    private Integer statusCode;

    @Column(name = "title", length = 1000)
    private String title;

    @Column(name = "meta_description", length = 2000)
    private String metaDescription;

    @Column(name = "canonical_url", length = 2048)
    private String canonicalUrl;

    @Column(name = "h1_count", nullable = false)
    private int h1Count;

    @Column(name = "h2_count", nullable = false)
    private int h2Count;

    @Column(name = "word_count", nullable = false)
    private int wordCount;

    @Column(name = "internal_links_count", nullable = false)
    private int internalLinksCount;

    @Column(name = "external_links_count", nullable = false)
    private int externalLinksCount;

    @Column(name = "images_count", nullable = false)
    private int imagesCount;

    @Column(name = "missing_alt_images_count", nullable = false)
    private int missingAltImagesCount;

    @Column(name = "load_time_ms")
    private Long loadTimeMs;

    @Column(name = "has_viewport_meta", nullable = false)
    private boolean hasViewportMeta;

    @Column(name = "is_https", nullable = false)
    private boolean isHttps;

    @Column(name = "content_hash", length = 64)
    private String contentHash;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
}
