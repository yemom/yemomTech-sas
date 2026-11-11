package com.YemomBizGrow.billing;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SubscriptionPlanRepository extends JpaRepository<SubscriptionPlan, UUID> {

    Optional<SubscriptionPlan> findBySlug(String slug);

    List<SubscriptionPlan> findAllByIsActiveTrueOrderBySortOrderAsc();
}
