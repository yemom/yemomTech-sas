package com.YemomBizGrow.seo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
@Table(name = "seo_audits")
@Getter
@Setter
@NoArgsConstructor
public class SeoAudit {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "website_id", nullable = false)
    private UUID websiteId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private SeoAuditStatus status = SeoAuditStatus.PENDING;

    @Column(name = "score")
    private Integer score;

    @Column(name = "technical_score")
    private Integer technicalScore;

    @Column(name = "on_page_score")
    private Integer onPageScore;

    @Column(name = "content_score")
    private Integer contentScore;

    @Column(name = "accessibility_score")
    private Integer accessibilityScore;

    @Column(name = "pages_crawled", nullable = false)
    private int pagesCrawled;

    @Column(name = "critical_issues_count", nullable = false)
    private int criticalIssuesCount;

    @Column(name = "warning_issues_count", nullable = false)
    private int warningIssuesCount;

    @Column(name = "info_issues_count", nullable = false)
    private int infoIssuesCount;

    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
}
