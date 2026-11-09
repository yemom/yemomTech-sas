package com.YemomBizGrow.billing;

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
@Table(name = "subscription_plans")
@Getter
@Setter
@NoArgsConstructor
public class SubscriptionPlan extends TimestampedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "slug", nullable = false, unique = true, length = 60)
    private String slug;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "price_etb", nullable = false, precision = 12, scale = 2)
    private BigDecimal priceEtb = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "billing_interval", nullable = false, length = 20)
    private BillingInterval billingInterval = BillingInterval.MONTHLY;

    @Column(name = "max_websites", nullable = false)
    private int maxWebsites = 1;

    @Column(name = "max_members", nullable = false)
    private int maxMembers = 3;

    @Column(name = "max_leads", nullable = false)
    private int maxLeads = 500;

    @Column(name = "max_campaigns", nullable = false)
    private int maxCampaigns = 2;

    @Column(name = "has_ai_insights", nullable = false)
    private boolean hasAiInsights = false;

    @Column(name = "has_agency_workspace", nullable = false)
    private boolean hasAgencyWorkspace = false;

    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder = 0;
}
