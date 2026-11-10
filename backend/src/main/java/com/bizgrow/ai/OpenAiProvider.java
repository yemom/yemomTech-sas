package com.YemomBizGrow.ai;

import com.YemomBizGrow.integration.google.GoogleAnalyticsSnapshotRepository.AnalyticsTotals;
import com.YemomBizGrow.organization.Organization;
import com.YemomBizGrow.seo.SeoAudit;
import com.YemomBizGrow.website.Website;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Real OpenAI / OpenAI-compatible Chat Completions AI Provider.
 *
 * <p>When configured with {@code OPENAI_API_KEY}, dispatches live completions to OpenAI
 * grounded strictly in persisted organization metrics. When unconfigured or upon API
 * failure, gracefully falls back to the deterministic {@link OfflineAiProvider}.</p>
 */
@Component
@Primary
public class OpenAiProvider implements AiProvider {

    private static final Logger log = LoggerFactory.getLogger(OpenAiProvider.class);

    private final String apiKey;
    private final String model;
    private final String baseUrl;
    private final OfflineAiProvider offlineProvider;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public OpenAiProvider(
            @Value("${app.ai.openai.api-key:}") String apiKey,
            @Value("${app.ai.openai.model:gpt-4o-mini}") String model,
            @Value("${app.ai.openai.base-url:https://api.openai.com/v1}") String baseUrl,
            OfflineAiProvider offlineProvider,
            ObjectMapper objectMapper) {
        this.apiKey = apiKey != null ? apiKey.trim() : "";
        this.model = (model != null && !model.isBlank()) ? model.trim() : "gpt-4o-mini";
        this.baseUrl = (baseUrl != null && !baseUrl.isBlank()) ? baseUrl.trim().replaceAll("/+$", "") : "https://api.openai.com/v1";
        this.offlineProvider = offlineProvider;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public boolean isConfigured() {
        return !apiKey.isBlank() && !apiKey.equalsIgnoreCase("mock") && !apiKey.equalsIgnoreCase("test");
    }

    @Override
    public List<AiRecommendation> generateRecommendations(
            Organization organization,
            List<Website> websites,
            Optional<SeoAudit> latestAudit,
            long totalLeads,
            long wonLeads,
            AnalyticsTotals totals) {

        if (!isConfigured()) {
            return offlineProvider.generateRecommendations(organization, websites, latestAudit, totalLeads, wonLeads, totals);
        }

        try {
            String systemPrompt = """
                    You are an expert growth strategist and AI business advisor for Ethiopian businesses on the YemomBizGrow SaaS platform.
                    Generate high-impact, actionable business and SEO recommendations grounded strictly in the provided verified data.
                    Never invent synthetic metrics or fake numbers. Return JSON format as an array of objects with keys:
                    title, category (SEO, CRM, MARKETING, or TECHNICAL), priority (HIGH, MEDIUM, or LOW), problem, evidence, expectedBenefit, actionRecommendation.
                    """;

            StringBuilder dataContext = new StringBuilder();
            dataContext.append("Business: ").append(organization.getName()).append(" (Industry: ").append(organization.getIndustry()).append(")\n");
            dataContext.append("Websites tracked: ").append(websites.size()).append("\n");
            if (latestAudit.isPresent()) {
                SeoAudit a = latestAudit.get();
                dataContext.append("SEO Health Score: ").append(a.getScore()).append("/100 (Critical: ")
                        .append(a.getCriticalIssuesCount()).append(", Warnings: ").append(a.getWarningIssuesCount()).append(")\n");
            } else {
                dataContext.append("SEO Audit: None completed yet.\n");
            }
            dataContext.append("CRM Leads: ").append(totalLeads).append(" total, ").append(wonLeads).append(" won deals.\n");
            if (totals != null) {
                dataContext.append("Traffic (30d): ").append(totals.getTotalSessions()).append(" sessions, ")
                        .append(Math.round(totals.getAvgBounceRate() * 100)).append("% bounce rate.\n");
            }

            Map<String, Object> requestBody = Map.of(
                    "model", model,
                    "response_format", Map.of("type", "json_object"),
                    "messages", List.of(
                            Map.of("role", "system", "content", systemPrompt),
                            Map.of("role", "user", "content", "Analyze this business data and provide recommendations:\n" + dataContext)
                    ),
                    "temperature", 0.3
            );

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/chat/completions"))
                    .timeout(Duration.ofSeconds(20))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(requestBody), StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() != 200) {
                log.warn("OpenAI API returned non-200 status {}: falling back to offline provider", response.statusCode());
                return offlineProvider.generateRecommendations(organization, websites, latestAudit, totalLeads, wonLeads, totals);
            }

            JsonNode root = objectMapper.readTree(response.body());
            String completionText = root.path("choices").get(0).path("message").path("content").asText();
            JsonNode parsedJson = objectMapper.readTree(completionText);
            JsonNode recsNode = parsedJson.has("recommendations") ? parsedJson.get("recommendations") : parsedJson;

            if (recsNode.isArray() && recsNode.size() > 0) {
                List<AiRecommendation> recommendations = new ArrayList<>();
                Instant now = Instant.now();
                UUID websiteId = websites.isEmpty() ? null : websites.get(0).getId();

                for (JsonNode item : recsNode) {
                    AiRecommendation rec = new AiRecommendation();
                    rec.setOrganizationId(organization.getId());
                    rec.setWebsiteId(websiteId);
                    rec.setCategory(parseCategory(item.path("category").asText("SEO")));
                    rec.setPriority(parsePriority(item.path("priority").asText("MEDIUM")));
                    rec.setTitle(item.path("title").asText("Strategic Recommendation"));
                    rec.setProblem(item.path("problem").asText(""));
                    rec.setEvidence(item.path("evidence").asText(""));
                    rec.setExpectedBenefit(item.path("expectedBenefit").asText(""));
                    rec.setActionRecommendation(item.path("actionRecommendation").asText(""));
                    rec.setCreatedAt(now);
                    rec.setUpdatedAt(now);
                    recommendations.add(rec);
                }
                return recommendations;
            }

        } catch (Exception e) {
            log.warn("OpenAI recommendation generation failed: {}; falling back to offline provider", e.getMessage());
        }

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

            List<Map<String, String>> messages = new ArrayList<>();
            messages.add(Map.of("role", "system", "content", systemInstructions));

            // Append FAQ context as system prompt content to guide the model
            StringBuilder promptBuilder = new StringBuilder(systemInstructions);
            if (faqContext != null && !faqContext.isBlank()) {
                promptBuilder.append("\n\nFAQ KNOWLEDGE BASE:\n").append(faqContext);
            }
            systemInstructions = promptBuilder.toString();

            List<Map<String, String>> messages = new ArrayList<>();
            messages.add(Map.of("role", "system", "content", systemInstructions));

            if (history != null) {
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
