package com.bizgrow.website;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WebsiteRepository extends JpaRepository<Website, UUID> {

    /** Organization-scoped uniqueness lookup (per-tenant domain dedup). */
    Optional<Website> findByOrganizationIdAndDomain(UUID organizationId, String domain);

    boolean existsByOrganizationIdAndDomain(UUID organizationId, String domain);

    /** Every read path is organization-scoped — there is intentionally no findAll() usage. */
    Page<Website> findAllByOrganizationIdOrderByCreatedAtDesc(UUID organizationId, Pageable pageable);

    Optional<Website> findByIdAndOrganizationId(UUID websiteId, UUID organizationId);

    /** Bounded dashboard feed — the only non-pagination read path. */
    List<Website> findTop5ByOrganizationIdOrderByCreatedAtDesc(UUID organizationId);

    long countByOrganizationIdAndStatus(UUID organizationId, WebsiteStatus status);
}
