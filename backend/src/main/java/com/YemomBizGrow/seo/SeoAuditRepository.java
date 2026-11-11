package com.YemomBizGrow.seo;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

public interface SeoAuditRepository extends JpaRepository<SeoAudit, UUID> {

    Page<SeoAudit> findByOrganizationIdAndWebsiteIdOrderByCreatedAtDesc(UUID organizationId, UUID websiteId, Pageable pageable);

    Optional<SeoAudit> findByIdAndOrganizationId(UUID id, UUID organizationId);

    Optional<SeoAudit> findByIdAndOrganizationIdAndWebsiteId(UUID id, UUID organizationId, UUID websiteId);

    Optional<SeoAudit> findFirstByOrganizationIdAndWebsiteIdAndStatusIn(UUID organizationId, UUID websiteId, Collection<SeoAuditStatus> statuses);

    Optional<SeoAudit> findFirstByOrganizationIdAndStatusOrderByCompletedAtDesc(UUID organizationId, SeoAuditStatus status);
}
