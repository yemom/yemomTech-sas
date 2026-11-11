package com.YemomBizGrow.organization;

import com.YemomBizGrow.common.exception.BadRequestException;
import com.YemomBizGrow.common.exception.NotFoundException;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Read-only view of organizations for OTHER modules (auth session bootstrap,
 * website registration's active-organization check, invitation acceptance).
 *
 * <p>Why a separate read service: modules like {@code website} only need "does
 * this org exist and is it active?" — giving them this narrow API keeps them from
 * depending on {@link OrganizationService} (the write/dashboard side), which
 * itself depends on the website module for dashboard counts. That keeps the
 * service dependency graph acyclic and free of constructor-injection loops.</p>
 */
@Service
public class OrganizationReadService {

    private final OrganizationRepository organizationRepository;

    public OrganizationReadService(OrganizationRepository organizationRepository) {
        this.organizationRepository = organizationRepository;
    }

    public boolean exists(UUID organizationId) {
        return organizationRepository.existsById(organizationId);
    }

    public Organization get(UUID organizationId) {
        return organizationRepository.findById(organizationId)
                .orElseThrow(() -> NotFoundException.resource("Organization"));
    }

    /** For writes against a tenant: archived/suspended organizations are frozen. */
    public void requireActive(UUID organizationId) {
        Organization organization = get(organizationId);
        if (organization.getStatus() != OrganizationStatus.ACTIVE) {
            throw new BadRequestException("This organization is not active.");
        }
    }

    public Map<UUID, Organization> findMap(Collection<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return Map.of();
        }
        return organizationRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Organization::getId, Function.identity()));
    }

    public List<Organization> findMany(Collection<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return organizationRepository.findAllById(ids);
    }
}
