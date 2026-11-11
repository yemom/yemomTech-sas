package com.YemomBizGrow.crm.dto;

import com.YemomBizGrow.crm.LeadActivity;
import com.YemomBizGrow.crm.LeadActivityType;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record LeadActivityResponse(
        UUID id,
        UUID organizationId,
        UUID leadId,
        UUID actorUserId,
        LeadActivityType activityType,
        String description,
        Map<String, Object> metadata,
        Instant createdAt
) {
    public static LeadActivityResponse from(LeadActivity activity) {
        return new LeadActivityResponse(
                activity.getId(),
                activity.getOrganizationId(),
                activity.getLeadId(),
                activity.getActorUserId(),
                activity.getActivityType(),
                activity.getDescription(),
                activity.getMetadata(),
                activity.getCreatedAt()
        );
    }
}
