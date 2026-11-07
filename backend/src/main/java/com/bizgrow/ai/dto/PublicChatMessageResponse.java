package com.YemomBizGrow.ai.dto;

import java.time.Instant;

public record PublicChatMessageResponse(
        String role,
        String content,
        Instant timestamp
) {}
