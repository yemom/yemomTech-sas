package com.YemomBizGrow.integration.google;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "google_analytics_snapshots",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_ga_snapshot_org_website_date",
                columnNames = {"organization_id", "website_id", "snapshot_date"}))
@Getter
@Setter
@NoArgsConstructor
public class GoogleAnalyticsSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "website_id", nullable = false)
    private UUID websiteId;

    @Column(name = "snapshot_date", nullable = false)
    private LocalDate snapshotDate;

    @Column(name = "sessions", nullable = false)
    private long sessions;

    @Column(name = "users", nullable = false)
    private long users;

    @Column(name = "new_users", nullable = false)
    private long newUsers;

    @Column(name = "page_views", nullable = false)
    private long pageViews;

    @Column(name = "bounce_rate", precision = 5, scale = 2)
    private BigDecimal bounceRate;

    @Column(name = "avg_session_sec")
    private Integer avgSessionSec;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "top_pages")
    private List<Map<String, Object>> topPages;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "devices")
    private List<Map<String, Object>> devices;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "countries")
    private List<Map<String, Object>> countries;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "channels")
    private List<Map<String, Object>> channels;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();
}
