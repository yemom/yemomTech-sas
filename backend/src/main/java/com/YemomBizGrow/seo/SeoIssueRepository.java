package com.YemomBizGrow.seo;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SeoIssueRepository extends JpaRepository<SeoIssue, UUID> {

    Page<SeoIssue> findByOrganizationIdAndAuditIdOrderBySeverityAscCreatedAtAsc(UUID organizationId, UUID auditId, Pageable pageable);

    Page<SeoIssue> findByOrganizationIdAndAuditIdAndSeverity(UUID organizationId, UUID auditId, String severity, Pageable pageable);

    Page<SeoIssue> findByOrganizationIdAndAuditIdAndCategory(UUID organizationId, UUID auditId, String category, Pageable pageable);

    List<SeoIssue> findByOrganizationIdAndAuditId(UUID organizationId, UUID auditId);
}
