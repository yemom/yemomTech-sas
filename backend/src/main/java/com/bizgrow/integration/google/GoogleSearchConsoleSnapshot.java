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
@Table(name = "google_search_console_snapshots",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_gsc_snapshot_org_website_date",
                columnNames = {"organization_id", "website_id", "snapshot_date"}))
@Getter
@Setter
@NoArgsConstructor
public class GoogleSearchConsoleSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "website_id", nullable = false)
    private UUID websiteId;

    @Column(name = "snapshot_date", nullable = false)
    private LocalDate snapshotDate;

    @Column(name = "total_clicks", nullable = false)
    private long totalClicks;

    @Column(name = "total_impressions", nullable = false)
    private long totalImpressions;

    @Column(name = "avg_ctr", precision = 6, scale = 4)
    private BigDecimal avgCtr;

    @Column(name = "avg_position", precision = 7, scale = 2)
    private BigDecimal avgPosition;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "top_queries")
    private List<Map<String, Object>> topQueries;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "top_pages")
    private List<Map<String, Object>> topPages;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();
}
