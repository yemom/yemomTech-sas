package com.YemomBizGrow.ai;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ChatbotMessageRepository extends JpaRepository<ChatbotMessage, UUID> {

    List<ChatbotMessage> findByConversationIdOrderByCreatedAtAsc(UUID conversationId);
}
