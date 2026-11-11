package com.YemomBizGrow.seo;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SeoCrawledPageRepository extends JpaRepository<SeoCrawledPage, UUID> {

    Page<SeoCrawledPage> findByOrganizationIdAndAuditIdOrderByCreatedAtAsc(UUID organizationId, UUID auditId, Pageable pageable);

    List<SeoCrawledPage> findByOrganizationIdAndAuditId(UUID organizationId, UUID auditId);
}
