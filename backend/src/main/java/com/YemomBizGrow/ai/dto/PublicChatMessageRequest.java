package com.YemomBizGrow.ai.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record PublicChatMessageRequest(
        @NotNull(message = "Session ID is required")
        UUID sessionId,

        @NotBlank(message = "Message content is required")
        @Size(max = 2000, message = "Message must not exceed 2000 characters")
        String message,

        String visitorName,
        String visitorEmail,
        String visitorPhone,

        UUID websiteId
) {}
