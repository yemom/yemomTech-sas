package com.YemomBizGrow.ai;

import com.YemomBizGrow.ai.dto.PublicChatMessageRequest;
import com.YemomBizGrow.ai.dto.PublicChatMessageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/public/chat")
@Tag(name = "Public Chatbot", description = "Public website widget chatbot endpoint")
public class PublicChatbotController {

    private final AiService aiService;

    public PublicChatbotController(AiService aiService) {
        this.aiService = aiService;
    }

    @PostMapping("/{organizationSlug}")
    @Operation(summary = "Send a message to an organization's public AI website chatbot")
    public PublicChatMessageResponse chat(
            @PathVariable String organizationSlug,
            @Valid @RequestBody PublicChatMessageRequest request) {
        return aiService.handlePublicChat(organizationSlug, request);
    }
}
