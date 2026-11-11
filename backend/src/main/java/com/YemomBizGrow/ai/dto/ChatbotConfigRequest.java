package com.YemomBizGrow.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Map;

public record ChatbotConfigRequest(
        @NotBlank(message = "Bot name is required")
        @Size(max = 128, message = "Bot name must not exceed 128 characters")
        String botName,

        @NotBlank(message = "Greeting message is required")
        String greetingMessage,

        String promptInstructions,

        @NotBlank(message = "Primary color is required")
        String primaryColor,

        List<Map<String, Object>> faqItems,

        boolean isEnabled
) {}
