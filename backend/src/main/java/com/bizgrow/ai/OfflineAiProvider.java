package com.YemomBizGrow.ai;

import com.YemomBizGrow.integration.google.GoogleAnalyticsSnapshotRepository.AnalyticsTotals;
import com.YemomBizGrow.organization.Organization;
import com.YemomBizGrow.seo.SeoAudit;
import com.YemomBizGrow.website.Website;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Component
public class OfflineAiProvider implements AiProvider {

    @Override
    public List<AiRecommendation> generateRecommendations(
            Organization organization,
            List<Website> websites,
            Optional<SeoAudit> latestAudit,
            long totalLeads,
            long wonLeads,
            AnalyticsTotals totals) {

        List<AiRecommendation> recommendations = new ArrayList<>();
        Instant now = Instant.now();

        // 1. Audit / Technical SEO recommendations
        if (latestAudit.isPresent()) {
            SeoAudit audit = latestAudit.get();
            int auditScore = audit.getScore() != null ? audit.getScore() : 100;
            if (auditScore < 80) {
                AiRecommendation rec = new AiRecommendation();
                rec.setOrganizationId(organization.getId());
                rec.setWebsiteId(audit.getWebsiteId());
                rec.setCategory(RecommendationCategory.SEO);
                rec.setPriority(auditScore < 60 ? RecommendationPriority.HIGH : RecommendationPriority.MEDIUM);
                rec.setTitle("Improve Website Technical SEO Health");
                rec.setProblem("Your latest SEO audit scored " + auditScore + "/100 with "
                        + audit.getCriticalIssuesCount() + " critical issues and " + audit.getWarningIssuesCount() + " warnings.");
                rec.setEvidence("Technical score: " + audit.getTechnicalScore() + ", Content score: " + audit.getContentScore());
                rec.setExpectedBenefit("Boost search ranking visibility on Google for local Ethiopian queries and eliminate crawl errors.");
                rec.setActionRecommendation("Review and resolve critical missing title tags, broken links, and missing image alt attributes identified in your audit report.");
                rec.setCreatedAt(now);
                rec.setUpdatedAt(now);
                recommendations.add(rec);
            }
        } else if (!websites.isEmpty()) {
            AiRecommendation rec = new AiRecommendation();
            rec.setOrganizationId(organization.getId());
            rec.setWebsiteId(websites.get(0).getId());
            rec.setCategory(RecommendationCategory.TECHNICAL);
            rec.setPriority(RecommendationPriority.HIGH);
            rec.setTitle("Run Initial Website SEO Audit");
            rec.setProblem("No comprehensive SEO audit has been conducted yet for " + websites.get(0).getDomain() + ".");
            rec.setEvidence("0 completed audits on record.");
            rec.setExpectedBenefit("Uncover indexing roadblocks, speed bottlenecks, and broken links preventing search discovery.");
            rec.setActionRecommendation("Trigger an automated SEO audit from the Websites workspace to assess page performance.");
            rec.setCreatedAt(now);
            rec.setUpdatedAt(now);
            recommendations.add(rec);
        }

        // 2. Lead Generation & Conversion recommendations
        if (totalLeads == 0) {
            AiRecommendation rec = new AiRecommendation();
            rec.setOrganizationId(organization.getId());
            rec.setCategory(RecommendationCategory.LEAD_GENERATION);
            rec.setPriority(RecommendationPriority.HIGH);
            rec.setTitle("Deploy Website Lead Capture Widget");
            rec.setProblem("You have not captured any inbound leads in your sales pipeline yet.");
            rec.setEvidence("Total active leads: 0.");
            rec.setExpectedBenefit("Convert website visitors into qualified prospective customers 24/7.");
            rec.setActionRecommendation("Embed the public BizGrow lead capture form widget on your homepage and contact pages to start collecting customer inquiries.");
            rec.setCreatedAt(now);
            rec.setUpdatedAt(now);
            recommendations.add(rec);
        } else if (totalLeads > 5) {
            double winRate = (double) wonLeads / totalLeads;
            if (winRate < 0.20) {
                AiRecommendation rec = new AiRecommendation();
                rec.setOrganizationId(organization.getId());
                rec.setCategory(RecommendationCategory.CONVERSION);
                rec.setPriority(RecommendationPriority.MEDIUM);
                rec.setTitle("Accelerate Lead Follow-up Speed");
                rec.setProblem("Only " + String.format(Locale.ROOT, "%.1f", winRate * 100) + "% of captured leads currently reach WON stage.");
                rec.setEvidence(wonLeads + " won out of " + totalLeads + " total leads.");
                rec.setExpectedBenefit("Increase sales conversion rate by up to 35% with systematic follow-ups within 2 hours of inquiry.");
                rec.setActionRecommendation("Implement phone calls or Telegram/WhatsApp outreach within 2 hours of lead capture to qualify interest rapidly.");
                rec.setCreatedAt(now);
                rec.setUpdatedAt(now);
                recommendations.add(rec);
            }
        }

        // 3. Traffic & Engagement recommendations
        if (totals != null && totals.getTotalSessions() > 50) {
            if (totals.getAvgBounceRate() > 55.0) {
                AiRecommendation rec = new AiRecommendation();
                rec.setOrganizationId(organization.getId());
                rec.setCategory(RecommendationCategory.CONTENT);
                rec.setPriority(RecommendationPriority.HIGH);
                rec.setTitle("Optimize Mobile Landing Page Experience");
                rec.setProblem("Your average bounce rate is high at "
                        + String.format(Locale.ROOT, "%.1f", totals.getAvgBounceRate()) + "%.");
                rec.setEvidence("Over half of website visitors leave without interacting with a second page.");
                rec.setExpectedBenefit("Keep visitors engaged, decrease bounce rate, and increase inquiry submission rate.");
                rec.setActionRecommendation("Add clear above-the-fold calls-to-action (Call Us / Telegram / Book Consultation) and improve mobile page load speed.");
                rec.setCreatedAt(now);
                rec.setUpdatedAt(now);
                recommendations.add(rec);
            }
        }

        // 4. Local Ethiopian Digital Presence recommendation
        AiRecommendation localRec = new AiRecommendation();
        localRec.setOrganizationId(organization.getId());
        localRec.setCategory(RecommendationCategory.CONTENT);
        localRec.setPriority(RecommendationPriority.LOW);
        localRec.setTitle("Enhance Local Brand Visibility (Addis Ababa & Ethiopia)");
        localRec.setProblem("Local search queries often search for specific Ethiopian services, locations, and direct contact options.");
        localRec.setEvidence("Market research shows over 80% of Ethiopian B2B buyers prefer fast phone, Telegram, or location directions.");
        localRec.setExpectedBenefit("Capture high-intent local searchers ready to purchase.");
        localRec.setActionRecommendation("Add your physical business address, Google Maps link, Ethio Telecom / Safaricom phone numbers, and Telegram link prominently in your footer and header.");
        localRec.setCreatedAt(now);
        localRec.setUpdatedAt(now);
        recommendations.add(localRec);

        return recommendations;
    }

    @Override
    public String generateChatReply(
            ChatbotConfig config,
            Organization organization,
            List<ChatbotMessage> history,
            String incomingMessage) {

        String lower = incomingMessage.toLowerCase(Locale.ROOT).trim();

        // 1. Check FAQ items configured by organization
        if (config.getFaqItems() != null) {
            for (Map<String, Object> faq : config.getFaqItems()) {
                String q = String.valueOf(faq.getOrDefault("question", "")).toLowerCase(Locale.ROOT);
                String a = String.valueOf(faq.getOrDefault("answer", ""));
                if (!q.isBlank() && !a.isBlank()) {
                    String[] words = q.split("\\s+");
                    int matchCount = 0;
                    for (String w : words) {
                        if (w.length() > 3 && lower.contains(w)) {
                            matchCount++;
                        }
                    }
                    if (matchCount >= 2 || (words.length <= 2 && matchCount >= 1)) {
                        return a;
                    }
                }
            }
        }

        // 2. Built-in contextual responses for typical business visitor queries
        if (lower.contains("hello") || lower.contains("hi") || lower.contains("selam") || lower.contains("hey")) {
            return config.getGreetingMessage() != null && !config.getGreetingMessage().isBlank()
                    ? config.getGreetingMessage()
                    : "Hello! Welcome to " + organization.getName() + ". How may I assist you today?";
        }

        if (lower.contains("price") || lower.contains("cost") || lower.contains("fee") || lower.contains("pricing") || lower.contains("how much")) {
            return "Our pricing is tailored to your business needs and project scope. Would you like to share your contact details (phone or email) so our team can provide a detailed quotation for " + organization.getName() + "?";
        }

        if (lower.contains("contact") || lower.contains("call") || lower.contains("phone") || lower.contains("reach") || lower.contains("address") || lower.contains("location")) {
            return "You can reach " + organization.getName() + " directly. Please leave your name and phone number here, or visit our contact page, and our representative will reach out to you promptly!";
        }

        if (lower.contains("service") || lower.contains("what do you do") || lower.contains("offer")) {
            return "At " + organization.getName() + ", we specialize in "
                    + (organization.getIndustry() != null ? organization.getIndustry() : "quality services")
                    + " designed to help your business achieve measurable results. Tell us what you're looking for, and we'd be delighted to help!";
        }

        // Default friendly helpful response
        return "Thank you for reaching out to " + organization.getName() + "! "
                + "Our team is here to help with your inquiry. Could you tell us a bit more about what you need, or leave your phone number or email so we can get back to you right away?";
    }
}
