package com.YemomBizGrow.ai;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ChatbotConversationRepository extends JpaRepository<ChatbotConversation, UUID> {

    Optional<ChatbotConversation> findByOrganizationIdAndSessionId(UUID organizationId, String sessionId);
}
