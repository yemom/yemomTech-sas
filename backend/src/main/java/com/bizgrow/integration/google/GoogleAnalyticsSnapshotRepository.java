package com.YemomBizGrow.integration.google;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GoogleAnalyticsSnapshotRepository extends JpaRepository<GoogleAnalyticsSnapshot, UUID> {

    Optional<GoogleAnalyticsSnapshot> findByOrganizationIdAndWebsiteIdAndSnapshotDate(
            UUID organizationId, UUID websiteId, LocalDate snapshotDate);

    List<GoogleAnalyticsSnapshot> findByOrganizationIdAndWebsiteIdAndSnapshotDateBetweenOrderBySnapshotDateAsc(
            UUID organizationId, UUID websiteId, LocalDate startDate, LocalDate endDate);

    List<GoogleAnalyticsSnapshot> findByOrganizationIdAndSnapshotDateBetweenOrderBySnapshotDateAsc(
            UUID organizationId, LocalDate startDate, LocalDate endDate);

    @Query("""
            SELECT COALESCE(SUM(s.sessions), 0) AS totalSessions,
                   COALESCE(SUM(s.users), 0) AS totalUsers,
                   COALESCE(SUM(s.pageViews), 0) AS totalPageViews,
                   COALESCE(AVG(s.bounceRate), 0) AS avgBounceRate
            FROM GoogleAnalyticsSnapshot s
            WHERE s.organizationId = :orgId
              AND (:websiteId IS NULL OR s.websiteId = :websiteId)
              AND s.snapshotDate BETWEEN :startDate AND :endDate
            """)
    AnalyticsTotals aggregateTotals(
            @Param("orgId") UUID orgId,
            @Param("websiteId") UUID websiteId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    interface AnalyticsTotals {
        long getTotalSessions();
        long getTotalUsers();
        long getTotalPageViews();
        double getAvgBounceRate();
    }
}
