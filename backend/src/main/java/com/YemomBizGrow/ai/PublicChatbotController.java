package com.YemomBizGrow.ai;

import com.YemomBizGrow.ai.dto.PublicChatMessageRequest;
import com.YemomBizGrow.ai.dto.PublicChatMessageResponse;
import com.YemomBizGrow.common.security.ClientIpResolver;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
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
    private final ClientIpResolver clientIpResolver;

    public PublicChatbotController(AiService aiService, @Autowired(required = false) ClientIpResolver clientIpResolver) {
        this.aiService = aiService;
        this.clientIpResolver = clientIpResolver;
    }

    @PostMapping("/{organizationSlug}")
    @Operation(summary = "Send a message to an organization's public AI website chatbot")
    public PublicChatMessageResponse chat(
            @PathVariable String organizationSlug,
            @Valid @RequestBody PublicChatMessageRequest request,
            HttpServletRequest servletRequest) {
        String clientIp = clientIpResolver != null ? clientIpResolver.resolveClientIp(servletRequest) : servletRequest.getRemoteAddr();
        return aiService.handlePublicChat(organizationSlug, request, clientIp);
    }
}
