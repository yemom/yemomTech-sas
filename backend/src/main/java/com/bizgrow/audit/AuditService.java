package com.bizgrow.audit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Writes the security/organization audit trail.
 *
 * <p>Deliberate properties:
 * <ul>
 *   <li><b>Fire-safe, never fatal:</b> audit persistence problems are logged and
 *       swallowed — an analytics/forensics concern must not roll back the business
 *       transaction the user actually asked for. (If regulation later demands
 *       strict audit durability, flip this to a throwing variant.)</li>
 *   <li><b>Secret scrubbing:</b> metadata keys containing password/token/secret/hash
 *       are dropped before writing, so a careless call site can never leak credentials
 *       into the trail.</li>
 *   <li><b>Append-only:</b> the repository exposes no update/delete paths for callers.</li>
 * </ul></p>
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
