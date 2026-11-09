package com.YemomBizGrow.crm;

import com.YemomBizGrow.common.entity.TimestampedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "crm_leads")
@Getter
@Setter
@NoArgsConstructor
public class Lead extends TimestampedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @Column(name = "last_name", length = 100)
    private String lastName;

    @Column(name = "email", length = 255)
    private String email;

    @Column(name = "phone_number", length = 50)
    private String phoneNumber;

    @Column(name = "company_name", length = 200)
    private String companyName;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 32)
    private LeadSource source = LeadSource.DIRECT;

    @Enumerated(EnumType.STRING)
    @Column(name = "stage", nullable = false, length = 32)
    private LeadStage stage = LeadStage.NEW;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private LeadStatus status = LeadStatus.ACTIVE;

    @Column(name = "assigned_user_id")
    private UUID assignedUserId;

    @Column(name = "campaign_id")
    private UUID campaignId;

    @Column(name = "estimated_value_etb", precision = 15, scale = 2)
    private BigDecimal estimatedValueEtb;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;
}
