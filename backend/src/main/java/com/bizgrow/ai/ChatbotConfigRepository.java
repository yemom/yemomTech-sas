package com.YemomBizGrow.ai;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ChatbotConfigRepository extends JpaRepository<ChatbotConfig, UUID> {

    Optional<ChatbotConfig> findByOrganizationId(UUID organizationId);
}
