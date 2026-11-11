package com.YemomBizGrow.marketing;

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
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "marketing_campaigns")
@Getter
@Setter
@NoArgsConstructor
public class MarketingCampaign extends TimestampedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "objective", nullable = false, length = 64)
    private CampaignObjective objective = CampaignObjective.LEAD_GENERATION;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private CampaignStatus status = CampaignStatus.DRAFT;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false, length = 64)
    private CampaignChannel channel = CampaignChannel.DIRECT;

    @Column(name = "budget_etb", nullable = false, precision = 15, scale = 2)
    private BigDecimal budgetEtb = BigDecimal.ZERO;

    @Column(name = "spent_etb", nullable = false, precision = 15, scale = 2)
    private BigDecimal spentEtb = BigDecimal.ZERO;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "utm_source", length = 100)
    private String utmSource;

    @Column(name = "utm_medium", length = 100)
    private String utmMedium;

    @Column(name = "utm_campaign", length = 100)
    private String utmCampaign;

    @Column(name = "utm_term", length = 100)
    private String utmTerm;

    @Column(name = "utm_content", length = 100)
    private String utmContent;
}
