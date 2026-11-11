package com.YemomBizGrow.billing;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface BillingHistoryRepository extends JpaRepository<BillingHistory, UUID> {

    Page<BillingHistory> findAllByOrganizationIdOrderByCreatedAtDesc(UUID organizationId, Pageable pageable);
}
