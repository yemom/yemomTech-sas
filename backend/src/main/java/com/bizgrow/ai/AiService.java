package com.YemomBizGrow.ai;

import com.YemomBizGrow.ai.dto.AiRecommendationResponse;
import com.YemomBizGrow.ai.dto.ChatbotConfigRequest;
import com.YemomBizGrow.ai.dto.ChatbotConfigResponse;
import com.YemomBizGrow.ai.dto.PublicChatMessageRequest;
import com.YemomBizGrow.ai.dto.PublicChatMessageResponse;
import com.YemomBizGrow.common.exception.BadRequestException;
import com.YemomBizGrow.common.exception.NotFoundException;
import com.YemomBizGrow.common.response.PageResponse;
import com.YemomBizGrow.crm.Lead;
import com.YemomBizGrow.crm.LeadRepository;
import com.YemomBizGrow.crm.LeadSource;
import com.YemomBizGrow.crm.LeadStage;
import com.YemomBizGrow.crm.LeadStatus;
import com.YemomBizGrow.integration.google.GoogleAnalyticsSnapshotRepository;
import com.YemomBizGrow.integration.google.GoogleAnalyticsSnapshotRepository.AnalyticsTotals;
import com.YemomBizGrow.membership.AccessControlService;
import com.YemomBizGrow.membership.Permission;
import com.YemomBizGrow.organization.Organization;
import com.YemomBizGrow.organization.OrganizationRepository;
import com.YemomBizGrow.seo.SeoAudit;
import com.YemomBizGrow.seo.SeoAuditRepository;
import com.YemomBizGrow.seo.SeoAuditStatus;
import com.YemomBizGrow.website.Website;
import com.YemomBizGrow.website.WebsiteRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class AiService {

    private static final Logger log = LoggerFactory.getLogger(AiService.class);

    private final AiRecommendationRepository recommendationRepository;
    private final ChatbotConfigRepository chatbotConfigRepository;
    private final ChatbotConversationRepository conversationRepository;
    private final ChatbotMessageRepository messageRepository;
    private final OrganizationRepository organizationRepository;
    private final WebsiteRepository websiteRepository;
    private final SeoAuditRepository seoAuditRepository;
    private final LeadRepository leadRepository;
    private final GoogleAnalyticsSnapshotRepository gaSnapshotRepository;
    private final AccessControlService accessControlService;
    private final AiProvider aiProvider;

    public AiService(
            AiRecommendationRepository recommendationRepository,
            ChatbotConfigRepository chatbotConfigRepository,
            ChatbotConversationRepository conversationRepository,
            ChatbotMessageRepository messageRepository,
            OrganizationRepository organizationRepository,
            WebsiteRepository websiteRepository,
            SeoAuditRepository seoAuditRepository,
            LeadRepository leadRepository,
            GoogleAnalyticsSnapshotRepository gaSnapshotRepository,
            AccessControlService accessControlService,
            AiProvider aiProvider) {
        this.recommendationRepository = recommendationRepository;
        this.chatbotConfigRepository = chatbotConfigRepository;
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.organizationRepository = organizationRepository;
        this.websiteRepository = websiteRepository;
        this.seoAuditRepository = seoAuditRepository;
        this.leadRepository = leadRepository;
        this.gaSnapshotRepository = gaSnapshotRepository;
        this.accessControlService = accessControlService;
        this.aiProvider = aiProvider;
    }

    @Transactional
    public PageResponse<AiRecommendationResponse> getRecommendations(
            UUID actorUserId,
            UUID organizationId,
            RecommendationStatus status,
            Pageable pageable) {
        accessControlService.requirePermission(actorUserId, organizationId, Permission.ORG_VIEW);

        long count = recommendationRepository.countByOrganizationIdAndStatus(organizationId, RecommendationStatus.OPEN);
        if (count == 0) {
            // Auto-generate initial set if empty
            generateRecommendationsInternal(organizationId);
        }

        Page<AiRecommendation> page = (status != null)
                ? recommendationRepository.findByOrganizationIdAndStatusOrderByCreatedAtDesc(organizationId, status, pageable)
                : recommendationRepository.findByOrganizationIdOrderByCreatedAtDesc(organizationId, pageable);

        return PageResponse.from(page.map(AiRecommendationResponse::from));
    }

    @Transactional
    public List<AiRecommendationResponse> generateRecommendations(UUID actorUserId, UUID organizationId) {
        accessControlService.requirePermission(actorUserId, organizationId, Permission.ORG_VIEW);
        List<AiRecommendation> created = generateRecommendationsInternal(organizationId);
        return created.stream().map(AiRecommendationResponse::from).toList();
    }

    @Transactional
    public AiRecommendationResponse updateStatus(
            UUID actorUserId,
            UUID organizationId,
            UUID recommendationId,
            RecommendationStatus newStatus) {
        accessControlService.requirePermission(actorUserId, organizationId, Permission.ORG_UPDATE);

        AiRecommendation rec = recommendationRepository.findByIdAndOrganizationId(recommendationId, organizationId)
                .orElseThrow(() -> new NotFoundException("Recommendation not found: " + recommendationId));

        rec.setStatus(newStatus);
        rec.setUpdatedAt(Instant.now());
        AiRecommendation saved = recommendationRepository.save(rec);

        return AiRecommendationResponse.from(saved);
    }

    @Transactional
    public ChatbotConfigResponse getChatbotConfig(UUID actorUserId, UUID organizationId) {
        accessControlService.requirePermission(actorUserId, organizationId, Permission.ORG_VIEW);

        ChatbotConfig config = chatbotConfigRepository.findByOrganizationId(organizationId)
                .orElseGet(() -> {
                    ChatbotConfig def = new ChatbotConfig();
                    def.setOrganizationId(organizationId);
                    def.setBotName("BizGrow Assistant");
                    def.setGreetingMessage("Selam! Welcome to our website. How can we help your business today?");
                    def.setPrimaryColor("#0f766e");
                    def.setEnabled(true);
                    return chatbotConfigRepository.save(def);
                });

        return ChatbotConfigResponse.from(config);
    }

    @Transactional
    public ChatbotConfigResponse updateChatbotConfig(
            UUID actorUserId,
            UUID organizationId,
            ChatbotConfigRequest request) {
        accessControlService.requirePermission(actorUserId, organizationId, Permission.ORG_UPDATE);

        ChatbotConfig config = chatbotConfigRepository.findByOrganizationId(organizationId)
                .orElseGet(() -> {
                    ChatbotConfig c = new ChatbotConfig();
                    c.setOrganizationId(organizationId);
                    return c;
                });

        config.setBotName(request.botName());
        config.setGreetingMessage(request.greetingMessage());
        config.setPromptInstructions(request.promptInstructions());
        config.setPrimaryColor(request.primaryColor());
        config.setFaqItems(request.faqItems());
        config.setEnabled(request.isEnabled());
        config.setUpdatedAt(Instant.now());

        ChatbotConfig saved = chatbotConfigRepository.save(config);
        return ChatbotConfigResponse.from(saved);
    }

    @Transactional
    public PublicChatMessageResponse handlePublicChat(String orgSlug, PublicChatMessageRequest request) {
        Organization organization = organizationRepository.findBySlug(orgSlug)
                .orElseThrow(() -> new NotFoundException("Organization not found: " + orgSlug));

        ChatbotConfig config = chatbotConfigRepository.findByOrganizationId(organization.getId())
                .orElseGet(() -> {
                    ChatbotConfig def = new ChatbotConfig();
                    def.setOrganizationId(organization.getId());
                    return def;
                });

        if (!config.isEnabled()) {
            throw new BadRequestException("Chatbot is currently disabled for this organization");
        }

        // Find or create conversation
        ChatbotConversation conversation = conversationRepository
                .findByOrganizationIdAndSessionId(organization.getId(), request.sessionId())
                .orElseGet(() -> {
                    ChatbotConversation conv = new ChatbotConversation();
                    conv.setOrganizationId(organization.getId());
                    conv.setSessionId(request.sessionId());
                    return conv;
                });

        if (request.visitorName() != null && !request.visitorName().isBlank()) {
            conversation.setVisitorName(request.visitorName().trim());
        }
        if (request.visitorEmail() != null && !request.visitorEmail().isBlank()) {
            conversation.setVisitorEmail(request.visitorEmail().trim().toLowerCase());
        }
        if (request.visitorPhone() != null && !request.visitorPhone().isBlank()) {
            conversation.setVisitorPhone(request.visitorPhone().trim());
        }
        conversation.setUpdatedAt(Instant.now());
        ChatbotConversation savedConv = conversationRepository.save(conversation);

        // Record User Message
        ChatbotMessage userMsg = new ChatbotMessage();
        userMsg.setConversationId(savedConv.getId());
        userMsg.setRole("USER");
        userMsg.setContent(request.message());
        messageRepository.save(userMsg);

        // Fetch history
        List<ChatbotMessage> history = messageRepository.findByConversationIdOrderByCreatedAtAsc(savedConv.getId());

        // Generate AI reply
        String replyText = aiProvider.generateChatReply(config, organization, history, request.message());

        // Record Assistant Message
        ChatbotMessage assistantMsg = new ChatbotMessage();
        assistantMsg.setConversationId(savedConv.getId());
        assistantMsg.setRole("ASSISTANT");
        assistantMsg.setContent(replyText);
        ChatbotMessage savedReply = messageRepository.save(assistantMsg);

        // Auto-create lead if visitor provided contact info
        if ((conversation.getVisitorEmail() != null || conversation.getVisitorPhone() != null)
                && conversation.getVisitorName() != null) {
            boolean leadExists = leadRepository.findByOrganizationIdAndEmailIgnoreCase(
                    organization.getId(), conversation.getVisitorEmail()).isPresent();
            if (!leadExists) {
                Lead lead = new Lead();
                lead.setOrganizationId(organization.getId());
                lead.setFirstName(conversation.getVisitorName());
                lead.setEmail(conversation.getVisitorEmail());
                lead.setPhoneNumber(conversation.getVisitorPhone());
                lead.setSource(LeadSource.CHATBOT);
                lead.setStage(LeadStage.NEW);
                lead.setStatus(LeadStatus.ACTIVE);
                lead.setNotes("Captured via AI website chatbot conversation session: " + request.sessionId());
                leadRepository.save(lead);
            }
        }

        return new PublicChatMessageResponse("ASSISTANT", savedReply.getContent(), savedReply.getCreatedAt());
    }

    private List<AiRecommendation> generateRecommendationsInternal(UUID organizationId) {
        Organization organization = organizationRepository.findById(organizationId)
                .orElseThrow(() -> new NotFoundException("Organization not found: " + organizationId));

        List<Website> websites = websiteRepository.findTop5ByOrganizationIdOrderByCreatedAtDesc(organizationId);

        Optional<SeoAudit> latestAudit = Optional.empty();
        if (!websites.isEmpty()) {
            latestAudit = seoAuditRepository
                    .findFirstByOrganizationIdAndWebsiteIdAndStatusIn(
                            organizationId,
                            websites.get(0).getId(),
                            List.of(SeoAuditStatus.COMPLETED));
        }

        long totalLeads = leadRepository.countByOrganizationIdAndStatus(organizationId, LeadStatus.ACTIVE);
        long wonLeads = leadRepository.countByOrganizationIdAndStage(organizationId, LeadStage.WON);

        AnalyticsTotals totals = gaSnapshotRepository.aggregateTotals(
                organizationId,
                null,
                LocalDate.now().minusDays(30),
                LocalDate.now());

        List<AiRecommendation> generated = aiProvider.generateRecommendations(
                organization,
                websites,
                latestAudit,
                totalLeads,
                wonLeads,
                totals);

        return recommendationRepository.saveAll(generated);
    }
}
