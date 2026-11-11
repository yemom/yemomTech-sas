package com.YemomBizGrow.audit.dto;

import com.YemomBizGrow.audit.AuditAction;
import com.YemomBizGrow.audit.AuditLog;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record AuditLogResponse(
        UUID id,
        UUID organizationId,
        UUID actorUserId,
        AuditAction action,
        String targetType,
        UUID targetId,
        Map<String, Object> metadata,
        Instant createdAt
) {
    public static AuditLogResponse from(AuditLog log) {
        return new AuditLogResponse(
                log.getId(),
                log.getOrganizationId(),
                log.getActorUserId(),
                log.getAction(),
                log.getTargetType(),
                log.getTargetId(),
                log.getMetadata(),
                log.getCreatedAt()
        );
    }
}
