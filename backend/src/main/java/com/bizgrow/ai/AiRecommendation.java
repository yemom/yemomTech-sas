package com.YemomBizGrow.ai;

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

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ai_recommendations")
@Getter
@Setter
@NoArgsConstructor
public class AiRecommendation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "website_id")
    private UUID websiteId;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 32)
    private RecommendationCategory category;

    @Enumerated(EnumType.STRING)
    @Column(name = "priority", nullable = false, length = 16)
    private RecommendationPriority priority;

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Column(name = "problem", nullable = false, columnDefinition = "TEXT")
    private String problem;

    @Column(name = "evidence", columnDefinition = "TEXT")
    private String evidence;

    @Column(name = "expected_benefit", columnDefinition = "TEXT")
    private String expectedBenefit;

    @Column(name = "action_recommendation", nullable = false, columnDefinition = "TEXT")
    private String actionRecommendation;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private RecommendationStatus status = RecommendationStatus.OPEN;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();
}
