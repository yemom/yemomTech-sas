package com.YemomBizGrow.crm.dto;

import com.YemomBizGrow.crm.Lead;
import com.YemomBizGrow.crm.LeadSource;
import com.YemomBizGrow.crm.LeadStage;
import com.YemomBizGrow.crm.LeadStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record LeadResponse(
        UUID id,
        UUID organizationId,
        String firstName,
        String lastName,
        String email,
        String phoneNumber,
        String companyName,
        LeadSource source,
        LeadStage stage,
        LeadStatus status,
        UUID assignedUserId,
        UUID campaignId,
        BigDecimal estimatedValueEtb,
        String notes,
        Instant createdAt,
        Instant updatedAt
) {
    public LeadResponse(
            UUID id,
            UUID organizationId,
            String firstName,
            String lastName,
            String email,
            String phoneNumber,
            String companyName,
            LeadSource source,
            LeadStage stage,
            LeadStatus status,
            UUID assignedUserId,
            BigDecimal estimatedValueEtb,
            String notes,
            Instant createdAt,
            Instant updatedAt
    ) {
        this(id, organizationId, firstName, lastName, email, phoneNumber, companyName, source, stage, status, assignedUserId, null, estimatedValueEtb, notes, createdAt, updatedAt);
    }

    public static LeadResponse from(Lead lead) {
        return new LeadResponse(
                lead.getId(),
                lead.getOrganizationId(),
                lead.getFirstName(),
                lead.getLastName(),
                lead.getEmail(),
                lead.getPhoneNumber(),
                lead.getCompanyName(),
                lead.getSource(),
                lead.getStage(),
                lead.getStatus(),
                lead.getAssignedUserId(),
                lead.getCampaignId(),
                lead.getEstimatedValueEtb(),
                lead.getNotes(),
                lead.getCreatedAt(),
                lead.getUpdatedAt()
        );
    }
}
