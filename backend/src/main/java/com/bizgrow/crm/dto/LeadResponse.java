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
        BigDecimal estimatedValueEtb,
        String notes,
        Instant createdAt,
        Instant updatedAt
) {
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
                lead.getEstimatedValueEtb(),
                lead.getNotes(),
                lead.getCreatedAt(),
                lead.getUpdatedAt()
        );
    }
}
