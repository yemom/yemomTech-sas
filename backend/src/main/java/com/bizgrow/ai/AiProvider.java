package com.YemomBizGrow.ai;

import com.YemomBizGrow.integration.google.GoogleAnalyticsSnapshotRepository.AnalyticsTotals;
import com.YemomBizGrow.organization.Organization;
import com.YemomBizGrow.seo.SeoAudit;
import com.YemomBizGrow.website.Website;

import java.util.List;
import java.util.Optional;

public interface AiProvider {

    List<AiRecommendation> generateRecommendations(
            Organization organization,
            List<Website> websites,
            Optional<SeoAudit> latestAudit,
            long totalLeads,
            long wonLeads,
            AnalyticsTotals totals);

    String generateChatReply(
            ChatbotConfig config,
            Organization organization,
            List<ChatbotMessage> history,
            String incomingMessage,
            String faqContext);
}
