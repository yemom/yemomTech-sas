package com.YemomBizGrow.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PublicChatMessageRequest(
        @NotBlank(message = "Session ID is required")
        String sessionId,

        @NotBlank(message = "Message content is required")
        @Size(max = 2000, message = "Message must not exceed 2000 characters")
        String message,

        String visitorName,
        String visitorEmail,
        String visitorPhone
) {}
