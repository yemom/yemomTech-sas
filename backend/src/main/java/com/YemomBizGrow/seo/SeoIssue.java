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
@Table(name = "seo_issues")
@Getter
@Setter
@NoArgsConstructor
public class SeoIssue {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "audit_id", nullable = false)
    private UUID auditId;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "website_id", nullable = false)
    private UUID websiteId;

    @Column(name = "category", nullable = false, length = 32)
    private String category;

    @Column(name = "severity", nullable = false, length = 16)
    private String severity;

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Column(name = "description", nullable = false, length = 2000)
    private String description;

    @Column(name = "affected_url", length = 2048)
    private String affectedUrl;

    @Column(name = "evidence", length = 2000)
    private String evidence;

    @Column(name = "explanation", length = 2000)
    private String explanation;

    @Column(name = "recommendation", length = 2000)
    private String recommendation;

    @Column(name = "status", nullable = false, length = 16)
    private String status = "OPEN";

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
}
