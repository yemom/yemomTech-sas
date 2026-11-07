package com.YemomBizGrow.ai.dto;

import com.YemomBizGrow.ai.ChatbotConfig;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record ChatbotConfigResponse(
        UUID id,
        UUID organizationId,
        String botName,
        String greetingMessage,
        String promptInstructions,
        String primaryColor,
        List<Map<String, Object>> faqItems,
        boolean isEnabled,
        Instant updatedAt
) {
    public static ChatbotConfigResponse from(ChatbotConfig entity) {
        return new ChatbotConfigResponse(
                entity.getId(),
                entity.getOrganizationId(),
                entity.getBotName(),
                entity.getGreetingMessage(),
                entity.getPromptInstructions(),
                entity.getPrimaryColor(),
                entity.getFaqItems(),
                entity.isEnabled(),
                entity.getUpdatedAt());
    }
}
