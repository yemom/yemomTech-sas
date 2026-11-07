package com.YemomBizGrow.integration.google;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GoogleSearchConsoleSnapshotRepository extends JpaRepository<GoogleSearchConsoleSnapshot, UUID> {

    Optional<GoogleSearchConsoleSnapshot> findByOrganizationIdAndWebsiteIdAndSnapshotDate(
            UUID organizationId, UUID websiteId, LocalDate snapshotDate);

    List<GoogleSearchConsoleSnapshot> findByOrganizationIdAndWebsiteIdAndSnapshotDateBetweenOrderBySnapshotDateAsc(
            UUID organizationId, UUID websiteId, LocalDate startDate, LocalDate endDate);

    @Query("""
            SELECT COALESCE(SUM(s.totalClicks), 0) AS totalClicks,
                   COALESCE(SUM(s.totalImpressions), 0) AS totalImpressions,
                   COALESCE(AVG(s.avgCtr), 0) AS avgCtr,
                   COALESCE(AVG(s.avgPosition), 0) AS avgPosition
            FROM GoogleSearchConsoleSnapshot s
            WHERE s.organizationId = :orgId
              AND (:websiteId IS NULL OR s.websiteId = :websiteId)
              AND s.snapshotDate BETWEEN :startDate AND :endDate
            """)
    SearchConsoleTotals aggregateTotals(
            @Param("orgId") UUID orgId,
            @Param("websiteId") UUID websiteId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    interface SearchConsoleTotals {
        long getTotalClicks();
        long getTotalImpressions();
        double getAvgCtr();
        double getAvgPosition();
    }
}
