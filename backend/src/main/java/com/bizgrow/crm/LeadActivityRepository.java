package com.YemomBizGrow.crm;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface LeadActivityRepository extends JpaRepository<LeadActivity, UUID> {

    Page<LeadActivity> findByOrganizationIdAndLeadIdOrderByCreatedAtDesc(UUID organizationId, UUID leadId, Pageable pageable);

    List<LeadActivity> findByOrganizationIdAndLeadIdOrderByCreatedAtDesc(UUID organizationId, UUID leadId);
}
