package com.YemomBizGrow.ai;

import com.YemomBizGrow.ai.dto.ChatbotConfigRequest;
import com.YemomBizGrow.ai.dto.ChatbotConfigResponse;
import com.YemomBizGrow.common.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/organizations/{organizationId}/chatbot/config")
@Tag(name = "Chatbot Configuration", description = "Tenant website chatbot customization and FAQ knowledge items")
public class ChatbotConfigController {

    private final AiService aiService;

    public ChatbotConfigController(AiService aiService) {
        this.aiService = aiService;
    }

    @GetMapping
    @Operation(summary = "Get chatbot configuration for the organization")
    public ChatbotConfigResponse getConfig(@PathVariable UUID organizationId) {
        UUID actorId = CurrentUser.require().id();
        return aiService.getChatbotConfig(actorId, organizationId);
    }

    @PutMapping
    @Operation(summary = "Update chatbot appearance, greeting, and FAQ knowledge")
    public ChatbotConfigResponse updateConfig(
            @PathVariable UUID organizationId,
            @Valid @RequestBody ChatbotConfigRequest request) {
        UUID actorId = CurrentUser.require().id();
        return aiService.updateChatbotConfig(actorId, organizationId, request);
    }
}
