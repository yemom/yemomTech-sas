package com.YemomBizGrow.ai;

import com.YemomBizGrow.integration.google.GoogleAnalyticsSnapshotRepository.AnalyticsTotals;
import com.YemomBizGrow.organization.Organization;
import com.YemomBizGrow.seo.SeoAudit;
import com.YemomBizGrow.website.Website;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

@Component
@Primary
public class OpenAiProvider implements AiProvider {

    private static final Logger log = LoggerFactory.getLogger(OpenAiProvider.class);

    private final String model;
    private final String apiKey;
    private final String baseUrl;
    private final AiProvider offlineProvider;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public OpenAiProvider(
            @Value("${app.ai.openai.model:gpt-4o-mini}") String model,
            @Value("${app.ai.openai.api-key:}") String apiKey,
            @Value("${app.ai.openai.base-url:https://api.openai.com/v1}") String baseUrl,
            @Qualifier("offlineAiProvider") AiProvider offlineProvider,
            ObjectMapper objectMapper) {
        this.model = model;
        this.apiKey = apiKey != null ? apiKey.trim() : "";
        this.baseUrl = (baseUrl != null && !baseUrl.isBlank()) ? baseUrl.trim().replaceAll("/+$", "") : "https://api.openai.com/v1";
        this.offlineProvider = offlineProvider;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    }

    @Override
    public List<AiRecommendation> generateRecommendations(
            Organization organization,
            List<Website> websites,
            Optional<SeoAudit> latestAudit,
            long totalLeads,
            long wonLeads,
            AnalyticsTotals totals) {
        return offlineProvider.generateRecommendations(organization, websites, latestAudit, totalLeads, wonLeads, totals);
    }

    @Override
    public String generateChatReply(
            ChatbotConfig config,
            Organization organization,
            List<ChatbotMessage> history,
            String incomingMessage,
            String faqContext) {
        if (!isConfigured()) {
            return offlineProvider.generateChatReply(config, organization, history, incomingMessage, faqContext);
        }

        try {
            String systemInstructions = (config.getPromptInstructions() != null && !config.getPromptInstructions().isBlank())
                    ? config.getPromptInstructions()
                    : "You are a professional customer service and sales assistant for " + organization.getName() + " on YemomBizGrow.";

            // Prepend FAQ context to system instructions if available
            if (faqContext != null && !faqContext.isBlank()) {
                systemInstructions = systemInstructions + "\n\nFAQ KNOWLEDGE BASE:\n" + faqContext;
            }

            List<Map<String, String>> messages = new ArrayList<>();
            messages.add(Map.of("role", "system", "content", systemInstructions));

            if (history != null && !history.isEmpty()) {
                for (ChatbotMessage msg : history) {
                    String role = "USER".equalsIgnoreCase(msg.getRole()) ? "user" : "assistant";
                    messages.add(Map.of("role", role, "content", msg.getContent()));
                }
            }
            messages.add(Map.of("role", "user", "content", incomingMessage));

            Map<String, Object> requestBody = Map.of(
                    "model", model,
                    "messages", messages,
                    "temperature", 0.5,
                    "max_tokens", 500
            );

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/chat/completions"))
                    .timeout(Duration.ofSeconds(15))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(requestBody), StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() == 200) {
                JsonNode root = objectMapper.readTree(response.body());
                return root.path("choices").get(0).path("message").path("content").asText().trim();
            } else {
                log.warn("OpenAI chat reply returned HTTP status {}: falling back to offline provider", response.statusCode());
            }
        } catch (Exception e) {
            log.warn("OpenAI chat reply failed: {}; falling back to offline provider", e.getMessage());
        }

        return offlineProvider.generateChatReply(config, organization, history, incomingMessage, faqContext);
    }

    private boolean isConfigured() {
        return !apiKey.isBlank() && !apiKey.equalsIgnoreCase("mock") && !apiKey.equalsIgnoreCase("test");
    }

    private RecommendationCategory parseCategory(String raw) {
        try {
            return RecommendationCategory.valueOf(raw.trim().toUpperCase());
        } catch (Exception e) {
            return RecommendationCategory.SEO;
        }
    }

    private RecommendationPriority parsePriority(String raw) {
        try {
            return RecommendationPriority.valueOf(raw.trim().toUpperCase());
        } catch (Exception e) {
            return RecommendationPriority.MEDIUM;
        }
    }
}
