package com.YemomBizGrow.ai;

import com.YemomBizGrow.organization.Organization;
import com.YemomBizGrow.seo.SeoAudit;
import com.YemomBizGrow.website.Website;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class OfflineAiProviderTest {

    private OfflineAiProvider provider;
    private Organization organization;

    @BeforeEach
    void setUp() {
        provider = new OfflineAiProvider();
        organization = new Organization();
        organization.setId(UUID.randomUUID());
        organization.setName("Addis Tech Solutions");
        organization.setIndustry("Technology & Software");
    }

    @Test
    void generateRecommendations_whenAuditScoreIsLow_producesSeoRecommendation() {
        SeoAudit audit = new SeoAudit();
        audit.setWebsiteId(UUID.randomUUID());
        audit.setScore(55);
        audit.setTechnicalScore(45);
        audit.setContentScore(60);
        audit.setCriticalIssuesCount(4);
        audit.setWarningIssuesCount(8);

        List<AiRecommendation> recs = provider.generateRecommendations(
                organization,
                Collections.emptyList(),
                Optional.of(audit),
                10,
                3,
                null);

        assertThat(recs).isNotEmpty();
        assertThat(recs.stream().anyMatch(r -> r.getCategory() == RecommendationCategory.SEO)).isTrue();
        AiRecommendation seoRec = recs.stream().filter(r -> r.getCategory() == RecommendationCategory.SEO).findFirst().orElseThrow();
        assertThat(seoRec.getPriority()).isEqualTo(RecommendationPriority.HIGH);
        assertThat(seoRec.getProblem()).contains("scored 55/100");
    }

    @Test
    void generateRecommendations_whenLeadsAreZero_producesLeadCaptureRecommendation() {
        List<AiRecommendation> recs = provider.generateRecommendations(
                organization,
                Collections.emptyList(),
                Optional.empty(),
                0,
                0,
                null);

        assertThat(recs).isNotEmpty();
        assertThat(recs.stream().anyMatch(r -> r.getCategory() == RecommendationCategory.LEAD_GENERATION)).isTrue();
    }

    @Test
    void generateChatReply_matchesConfiguredFaqQuestion() {
        ChatbotConfig config = new ChatbotConfig();
        config.setBotName("Support Bot");
        config.setGreetingMessage("Welcome!");
        List<Map<String, Object>> faqItems = List.of(
                Map.of("question", "What are your business opening hours?", "answer", "We are open Monday to Friday from 8:30 AM to 5:30 PM (EAT)."),
                Map.of("question", "Where is your office located?", "answer", "Our office is in Bole, Addis Ababa, near Edna Mall.")
        );
        // Build FAQ context string from config FAQ items (format: "question: answer\n\n")
        StringBuilder faqContextBuilder = new StringBuilder();
        for (Map<String, Object> faq : faqItems) {
            String question = (String) faq.get("question");
            String answer = (String) faq.get("answer");
            if (question != null && answer != null) {
                faqContextBuilder.append(question).append(": ").append(answer).append("\n\n");
            }
        }
        String faqContext = faqContextBuilder.toString();

        ChatbotConfig configWithFaq = new ChatbotConfig();
        configWithFaq.setBotName("Support Bot");
        configWithFaq.setGreetingMessage("Welcome!");
        configWithFaq.setFaqItems(faqItems);

        String reply1 = provider.generateChatReply(configWithFaq, organization, Collections.emptyList(), "What are your opening hours?", faqContext);
        assertThat(reply1).isEqualTo("We are open Monday to Friday from 8:30 AM to 5:30 PM (EAT).");

        String reply2 = provider.generateChatReply(configWithFaq, organization, Collections.emptyList(), "Where is your office located?", faqContext);
        assertThat(reply2).isEqualTo("Our office is in Bole, Addis Ababa, near Edna Mall.");
    }

    @Test
    void generateChatReply_handlesGreetingAndPricingFallback() {
        ChatbotConfig config = new ChatbotConfig();
        config.setGreetingMessage("Selam from Addis Tech!");

        String greeting = provider.generateChatReply(config, organization, Collections.emptyList(), "Selam! How are you?", "");
        assertThat(greeting).contains("Selam");

        String pricing = provider.generateChatReply(config, organization, Collections.emptyList(), "How much does your service cost?", "");
        assertThat(pricing).contains("pricing is tailored");
    }
}
