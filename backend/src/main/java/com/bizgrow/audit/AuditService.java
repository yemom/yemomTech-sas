package com.YemomBizGrow.audit;

import com.YemomBizGrow.audit.dto.AuditLogResponse;
import com.YemomBizGrow.common.response.PageResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Writes and queries the security/organization audit trail.
 */
@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);
    private static final Set<String> SECRET_KEY_HINTS = Set.of("password", "token", "secret", "credential", "hash");

    private final AuditLogRepository repository;

    public AuditService(AuditLogRepository repository) {
        this.repository = repository;
    }

    public void record(UUID actorUserId, UUID organizationId, AuditAction action,
                       String targetType, UUID targetId, Map<String, Object> metadata) {
        try {
            AuditLog entry = new AuditLog();
            entry.setActorUserId(actorUserId);
            entry.setOrganizationId(organizationId);
            entry.setAction(action);
            entry.setTargetType(targetType);
            entry.setTargetId(targetId);
            entry.setMetadata(sanitize(metadata));
            repository.save(entry);
        } catch (RuntimeException e) {
            log.warn("Audit write failed for {} (actor={}, org={}): {}",
                    action, actorUserId, organizationId, e.getMessage());
        }
    }

    public PageResponse<AuditLogResponse> listByOrganization(UUID organizationId, Pageable pageable) {
        Page<AuditLog> page = repository.findByOrganizationIdOrderByCreatedAtDesc(organizationId, pageable);
        return PageResponse.from(page.map(AuditLogResponse::from));
    }

    private Map<String, Object> sanitize(Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return null;
        }
        Map<String, Object> clean = new LinkedHashMap<>();
        metadata.forEach((key, value) -> {
            String k = key == null ? "" : key.toLowerCase(Locale.ROOT);
            boolean looksSecret = SECRET_KEY_HINTS.stream().anyMatch(k::contains);
            if (!looksSecret) {
                clean.put(key, value);
            }
        });
        return clean.isEmpty() ? null : clean;
    }
}
