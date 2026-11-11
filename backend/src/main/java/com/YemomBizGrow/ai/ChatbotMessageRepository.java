package com.YemomBizGrow.ai;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface ChatbotMessageRepository extends JpaRepository<ChatbotMessage, UUID> {

    List<ChatbotMessage> findByConversationIdOrderByCreatedAtAsc(UUID conversationId);

    List<ChatbotMessage> findTop10ByConversationIdOrderByCreatedAtDesc(UUID conversationId);

    long countByConversationSessionIdAndCreatedAfter(String sessionId, Instant createdAfter);
}
