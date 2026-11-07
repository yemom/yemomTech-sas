package com.YemomBizGrow.crm;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LeadRepository extends JpaRepository<Lead, UUID> {

    Optional<Lead> findByIdAndOrganizationId(UUID id, UUID organizationId);

    Page<Lead> findByOrganizationIdAndStatusOrderByCreatedAtDesc(UUID organizationId, LeadStatus status, Pageable pageable);

    Page<Lead> findByOrganizationIdAndStatusAndStageOrderByCreatedAtDesc(UUID organizationId, LeadStatus status, LeadStage stage, Pageable pageable);

    List<Lead> findByOrganizationIdAndStatus(UUID organizationId, LeadStatus status);

    Optional<Lead> findByOrganizationIdAndEmailIgnoreCase(UUID organizationId, String email);

    long countByOrganizationIdAndStatus(UUID organizationId, LeadStatus status);

    long countByOrganizationIdAndStage(UUID organizationId, LeadStage stage);

    @Query("SELECT l FROM Lead l WHERE l.organizationId = :orgId AND l.status = 'ACTIVE' AND (" +
            "LOWER(l.firstName) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
            "LOWER(l.lastName) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
            "LOWER(l.email) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
            "LOWER(l.companyName) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
            "LOWER(l.phoneNumber) LIKE LOWER(CONCAT('%', :query, '%')))")
    Page<Lead> searchLeads(@Param("orgId") UUID orgId, @Param("query") String query, Pageable pageable);

    interface StageSummaryView {
        LeadStage getStage();
        long getCount();
        BigDecimal getTotalValue();
    }

    @Query("SELECT l.stage as stage, COUNT(l) as count, COALESCE(SUM(l.estimatedValueEtb), 0) as totalValue " +
            "FROM Lead l WHERE l.organizationId = :orgId AND l.status = 'ACTIVE' GROUP BY l.stage")
    List<StageSummaryView> getPipelineSummary(@Param("orgId") UUID orgId);
}
